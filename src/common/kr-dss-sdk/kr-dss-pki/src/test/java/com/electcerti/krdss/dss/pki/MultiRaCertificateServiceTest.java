package com.electcerti.krdss.dss.pki;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 특허-B 청구항 2·13·14 — Multi-RA 단일 등록 → 복수 인증서, SKI 연관 식별·추가 발급.
 */
class MultiRaCertificateServiceTest {

    private static final byte[] AAGUID = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    private static final byte[] CRED_ID = {5, 5, 5, 5};

    private final RegistrationAuthority raA = new RegistrationAuthority(
            "RA-A", "신원확인기관 A", RegistrationAuthority.RaType.IDENTITY, KrPkiOids.raPolicy("1"));
    private final RegistrationAuthority raB = new RegistrationAuthority(
            "RA-B", "플랫폼기관 B", RegistrationAuthority.RaType.PLATFORM, KrPkiOids.raPolicy("2"));
    private final RegistrationAuthority raC = new RegistrationAuthority(
            "RA-C", "신원확인기관 C", RegistrationAuthority.RaType.IDENTITY, KrPkiOids.raPolicy("3"));

    private MultiRaCertificateService newService() {
        var ca = new CertificateAuthority("ca.kr-dss.example");
        var registry = new InMemoryAuthenticatorMetadataRegistry().register(AAGUID, AuthenticatorGrade.HIGH);
        return new MultiRaCertificateService(new RegistrationBindingService(ca, registry));
    }

    @Test
    void single_registration_yields_multiple_certs_same_spki_same_ski_distinct_ra_policy() throws Exception {
        KeyPair credential = PkiTestSupport.newCredentialKeyPair();
        var reg = PkiTestSupport.verifiedRegistration(credential, AAGUID, CRED_ID);
        var svc = newService();

        List<IssuedCertificate> issued = svc.issueForAll(reg, List.of(raA, raB, raC), "홍길동");

        assertThat(issued).hasSize(3);

        // 동일 SubjectPublicKeyInfo
        byte[] spki = credential.getPublic().getEncoded();
        for (IssuedCertificate ic : issued) {
            assertThat(ic.certificate().getPublicKey().getEncoded()).isEqualTo(spki);
        }

        // 동일 SubjectKeyIdentifier (청구항 13)
        var skis = issued.stream()
                .map(ic -> safeSki(ic.certificate()))
                .distinct().toList();
        assertThat(skis).hasSize(1);
        assertThat(issued.stream().map(IssuedCertificate::keyIdentifierHex).distinct()).hasSize(1);

        // RA 별 서로 다른 정책 OID
        assertThat(policyOf(issued, "RA-A")).contains(raA.policyOid()).doesNotContain(raB.policyOid());
        assertThat(policyOf(issued, "RA-B")).contains(raB.policyOid()).doesNotContain(raA.policyOid());
        assertThat(policyOf(issued, "RA-C")).contains(raC.policyOid());
    }

    @Test
    void findByKeyIdentifier_returns_all_related_certs_in_O1() throws Exception {
        KeyPair credential = PkiTestSupport.newCredentialKeyPair();
        var reg = PkiTestSupport.verifiedRegistration(credential, AAGUID, CRED_ID);
        var svc = newService();

        svc.issueForAll(reg, List.of(raA, raB, raC), "홍길동");
        String kid = CertificateAuthority.keyIdentifierHex(credential.getPublic());

        assertThat(svc.findByKeyIdentifier(kid)).hasSize(3);
        assertThat(svc.findByKeyIdentifier("deadbeef")).isEmpty();
    }

    @Test
    void addon_issuance_reuses_registration_without_reregistration() throws Exception {
        KeyPair credential = PkiTestSupport.newCredentialKeyPair();
        var reg = PkiTestSupport.verifiedRegistration(credential, AAGUID, CRED_ID);
        var svc = newService();

        svc.issueForAll(reg, List.of(raA, raB), "홍길동");
        String kid = CertificateAuthority.keyIdentifierHex(credential.getPublic());
        assertThat(svc.findByKeyIdentifier(kid)).hasSize(2);

        // 청구항 14: 추가 RA 와 관계 → 동일 등록 결과로 추가 발급(재등록 없음)
        var addon = svc.issueForRa(reg, raC, "홍길동");

        assertThat(addon.keyIdentifierHex()).isEqualTo(kid);
        assertThat(svc.findByKeyIdentifier(kid)).hasSize(3);
    }

    private static String safeSki(java.security.cert.X509Certificate cert) {
        try {
            return PkiTestSupport.subjectKeyIdentifier(cert);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static List<String> policyOf(List<IssuedCertificate> issued, String raId) {
        return issued.stream()
                .filter(ic -> ic.raId().equals(raId))
                .findFirst()
                .map(ic -> {
                    try {
                        return PkiTestSupport.policyOids(ic.certificate());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElseThrow();
    }
}
