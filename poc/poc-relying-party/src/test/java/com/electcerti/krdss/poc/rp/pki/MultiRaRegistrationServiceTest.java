package com.electcerti.krdss.poc.rp.pki;

import com.electcerti.krdss.dss.pki.CertificateStatus;
import com.electcerti.krdss.poc.rp.local.WebAuthnDemoCa;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 특허-B PoC 배선 — 단일 등록 → 복수 RA 인증서 + 선택적 폐지 + HSM 발급(서비스 계층).
 */
class MultiRaRegistrationServiceTest {

    private MultiRaRegistrationService newService() {
        return new MultiRaRegistrationService(new WebAuthnDemoCa());
    }

    private static KeyPair ec() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        return kpg.generateKeyPair();
    }

    private static String b64url(byte[] b) {
        return Base64.getUrlEncoder().encodeToString(b);
    }

    @Test
    void single_registration_issues_certificates_for_all_ras_with_shared_kid() throws Exception {
        var svc = newService();
        KeyPair credential = ec();
        String credId = b64url("cred-multi-1".getBytes(StandardCharsets.UTF_8));

        List<MultiRaRegistrationService.CertView> certs = svc.registerMulti(
                credential.getPublic().getEncoded(), credId, -7, new byte[16], true);

        // RA 수만큼 인증서, 동일 KID, RA별 다른 정책 OID
        assertThat(certs).hasSize(svc.registrationAuthorities().size()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(certs.stream().map(MultiRaRegistrationService.CertView::keyIdentifier).distinct()).hasSize(1);
        assertThat(certs.stream().map(MultiRaRegistrationService.CertView::policyOid).distinct())
                .hasSize(certs.size());
        assertThat(certs).allMatch(c -> "VALID".equals(c.status()));
    }

    @Test
    void selective_revocation_keeps_other_ra_certificates_valid() throws Exception {
        var svc = newService();
        KeyPair credential = ec();
        String credId = b64url("cred-multi-2".getBytes(StandardCharsets.UTF_8));
        var certs = svc.registerMulti(credential.getPublic().getEncoded(), credId, -7, new byte[16], true);

        var first = certs.get(0);
        svc.revoke(first.serial());

        assertThat(svc.status(first.serial())).isEqualTo(CertificateStatus.REVOKED);
        for (int i = 1; i < certs.size(); i++) {
            assertThat(svc.status(certs.get(i).serial())).isEqualTo(CertificateStatus.VALID);
        }
        // 연관 조회는 발급된 전체를 반환(폐지 여부와 무관하게 관계는 유지)
        assertThat(svc.findByKeyIdentifier(first.keyIdentifier())).hasSize(certs.size());
    }

    @Test
    void renewal_redetermines_grade_on_reverification() throws Exception {
        var svc = newService();
        KeyPair credential = ec();
        String credId = b64url("cred-multi-3".getBytes(StandardCharsets.UTF_8));
        byte[] aaguid = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
        svc.seedAuthenticatorGrade(aaguid, com.electcerti.krdss.dss.pki.AuthenticatorGrade.HIGH);

        var certs = svc.registerMulti(credential.getPublic().getEncoded(), credId, -7, aaguid, true);
        assertThat(certs.get(0).grade()).isEqualTo("HIGH");

        // 갱신 시 attestation 미검증 → UNKNOWN 강등
        var renewed = svc.renew(certs.get(0).serial(), false);
        assertThat(renewed.grade()).isEqualTo("UNKNOWN");
        assertThat(svc.status(certs.get(0).serial())).isEqualTo(CertificateStatus.REVOKED);
    }

    @Test
    void hsm_certificate_issued_from_csr_and_attestation() throws Exception {
        var svc = newService();
        KeyPair hsmKey = ec();
        byte[] csr = buildCsr(hsmKey, "원격서명자");
        byte[] deviceId = {10, 20, 30, 40};
        svc.seedHsmDevice(deviceId, com.electcerti.krdss.dss.pki.HsmGrade.HIGH);

        var view = svc.issueHsm(csr, deviceId, new byte[]{1, 2}, true, "EAL4+", true, "RA-BANK");

        assertThat(view.grade()).isEqualTo("HIGH");
        assertThat(view.nonExtractable()).isTrue();
        assertThat(view.certificatePem()).contains("BEGIN CERTIFICATE");
    }

    @Test
    void hsm_issue_rejects_unverified_attestation() throws Exception {
        var svc = newService();
        KeyPair hsmKey = ec();
        byte[] csr = buildCsr(hsmKey, "원격서명자");

        assertThatThrownBy(() -> svc.issueHsm(csr, new byte[]{9}, new byte[0], true, "EAL4+", false, "RA-BANK"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static byte[] buildCsr(KeyPair key, String cn) throws Exception {
        var builder = new JcaPKCS10CertificationRequestBuilder(new X500Name("CN=" + cn), key.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(key.getPrivate());
        return builder.build(signer).getEncoded();
    }
}
