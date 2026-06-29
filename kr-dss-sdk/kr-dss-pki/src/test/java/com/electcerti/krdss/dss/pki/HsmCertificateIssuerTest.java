package com.electcerti.krdss.dss.pki;

import com.electcerti.krdss.ades.cades.KrAdesOids;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 특허-B 발명 B-4 (청구항 10·15) — CSR + HSM Attestation 기반 인증서 발급.
 */
class HsmCertificateIssuerTest {

    private static final byte[] HSM_DEVICE = {10, 20, 30, 40};
    private static final byte[] HSM_INSTANCE = {1, 2, 3};

    private final RegistrationAuthority ra = new RegistrationAuthority(
            "RA-HSM", "원격서명기관", RegistrationAuthority.RaType.PLATFORM, KrPkiOids.raPolicy("9"));

    private HsmCertificateIssuer newIssuer(HsmDeviceRegistry registry) {
        var ca = new CertificateAuthority("ca.kr-dss.example");
        return new HsmCertificateIssuer(ca, new HsmAttestationVerifier(registry));
    }

    /** HSM 키쌍으로 PKCS#10 CSR(DER)을 생성한다. */
    private static byte[] buildCsr(KeyPair hsmKey, String cn) throws Exception {
        var builder = new JcaPKCS10CertificationRequestBuilder(new X500Name("CN=" + cn), hsmKey.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(hsmKey.getPrivate());
        return builder.build(signer).getEncoded();
    }

    private static HsmAttestation attestation(boolean nonExtractable, boolean sigVerified) {
        return new HsmAttestation(HSM_DEVICE, HSM_INSTANCE, nonExtractable, "EAL4+", sigVerified);
    }

    @Test
    void issues_cert_with_csr_publicKey_and_hsm_grade_and_ra_policies() throws Exception {
        KeyPair hsmKey = PkiTestSupport.newCredentialKeyPair();
        var issuer = newIssuer(new InMemoryHsmDeviceRegistry().register(HSM_DEVICE, HsmGrade.HIGH));

        var issued = issuer.issueFromCsr(buildCsr(hsmKey, "원격서명자"), attestation(true, true), ra, "원격서명자");

        // CSR 공개키 == 인증서 SubjectPublicKeyInfo (청구항 10)
        assertThat(issued.certificate().getPublicKey().getEncoded())
                .isEqualTo(hsmKey.getPublic().getEncoded());
        // 검증경로(HSM) + 등급(청15) + RA 정책
        assertThat(PkiTestSupport.policyOids(issued.certificate()))
                .contains(KrAdesOids.POLICY_HSM, KrPkiOids.HSM_GRADE_HIGH, ra.policyOid());
        assertThat(issued.grade()).isEqualTo(HsmGrade.HIGH);
        assertThat(issued.nonExtractable()).isTrue();
    }

    @Test
    void non_extractable_false_caps_grade_to_low() throws Exception {
        KeyPair hsmKey = PkiTestSupport.newCredentialKeyPair();
        var issuer = newIssuer(new InMemoryHsmDeviceRegistry().register(HSM_DEVICE, HsmGrade.HIGH));

        // 키 비추출 미보장 → HIGH 라도 LOW 로 상한 제한
        var issued = issuer.issueFromCsr(buildCsr(hsmKey, "원격서명자"), attestation(false, true), ra, "원격서명자");

        assertThat(issued.grade()).isEqualTo(HsmGrade.LOW);
        assertThat(PkiTestSupport.policyOids(issued.certificate())).contains(KrPkiOids.HSM_GRADE_LOW);
    }

    @Test
    void rejects_unverified_attestation() throws Exception {
        KeyPair hsmKey = PkiTestSupport.newCredentialKeyPair();
        var issuer = newIssuer(new InMemoryHsmDeviceRegistry().register(HSM_DEVICE, HsmGrade.HIGH));
        byte[] csr = buildCsr(hsmKey, "원격서명자");

        // attestationSig 미검증 → 발급 거부 (청구항 10)
        assertThatThrownBy(() -> issuer.issueFromCsr(csr, attestation(true, false), ra, "원격서명자"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejects_tampered_csr() throws Exception {
        KeyPair hsmKey = PkiTestSupport.newCredentialKeyPair();
        var issuer = newIssuer(new InMemoryHsmDeviceRegistry().register(HSM_DEVICE, HsmGrade.HIGH));
        byte[] csr = buildCsr(hsmKey, "원격서명자");
        csr[csr.length - 1] ^= 0x01; // 서명 영역 변조 → 소유증명 검증 실패

        assertThatThrownBy(() -> issuer.issueFromCsr(csr, attestation(true, true), ra, "원격서명자"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
