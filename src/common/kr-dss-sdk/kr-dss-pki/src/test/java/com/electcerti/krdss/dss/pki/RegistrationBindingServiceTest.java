package com.electcerti.krdss.dss.pki;

import com.electcerti.krdss.ades.cades.KrAdesOids;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 특허-B 청구항 1·5·11 — Registration Binding + 장치 등급 판정.
 */
class RegistrationBindingServiceTest {

    private static final byte[] AAGUID_HIGH = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    private static final byte[] CRED_ID = {9, 8, 7, 6};

    private RegistrationBindingService service(AuthenticatorMetadataRegistry registry) {
        return new RegistrationBindingService(new CertificateAuthority("ca.kr-dss.example"), registry);
    }

    private final RegistrationAuthority raA = new RegistrationAuthority(
            "RA-A", "신원확인기관 A", RegistrationAuthority.RaType.IDENTITY, KrPkiOids.raPolicy("1"));

    @Test
    void credential_publicKey_becomes_certificate_spki() throws Exception {
        KeyPair credential = PkiTestSupport.newCredentialKeyPair();
        var svc = service(new InMemoryAuthenticatorMetadataRegistry().register(AAGUID_HIGH, AuthenticatorGrade.HIGH));

        var issued = svc.bind(PkiTestSupport.verifiedRegistration(credential, AAGUID_HIGH, CRED_ID), raA, "홍길동");

        // 청구항 5: Credential 공개키 == 인증서 SubjectPublicKeyInfo
        assertThat(issued.certificate().getPublicKey().getEncoded())
                .isEqualTo(credential.getPublic().getEncoded());
    }

    @Test
    void certificate_carries_verification_grade_and_ra_policies() throws Exception {
        KeyPair credential = PkiTestSupport.newCredentialKeyPair();
        var svc = service(new InMemoryAuthenticatorMetadataRegistry().register(AAGUID_HIGH, AuthenticatorGrade.HIGH));

        var issued = svc.bind(PkiTestSupport.verifiedRegistration(credential, AAGUID_HIGH, CRED_ID), raA, "홍길동");

        // 검증 경로(특허-A) + 등급(청구항 11) + RA 식별(청구항 2) 정책이 모두 포함
        assertThat(PkiTestSupport.policyOids(issued.certificate()))
                .contains(KrAdesOids.POLICY_WEBAUTHN, KrPkiOids.GRADE_HIGH, raA.policyOid());
        assertThat(issued.grade()).isEqualTo(AuthenticatorGrade.HIGH);
    }

    @Test
    void grade_downgraded_to_unknown_when_attestation_not_verified() throws Exception {
        KeyPair credential = PkiTestSupport.newCredentialKeyPair();
        var svc = service(new InMemoryAuthenticatorMetadataRegistry().register(AAGUID_HIGH, AuthenticatorGrade.HIGH));

        var reg = new RegistrationResult(credential.getPublic(), AAGUID_HIGH, CRED_ID, -7,
                AttestationVerificationResult.unverified());
        var issued = svc.bind(reg, raA, "홍길동");

        // attestation 미검증이면 AAGUID 가 HIGH 라도 UNKNOWN 으로 강등
        assertThat(issued.grade()).isEqualTo(AuthenticatorGrade.UNKNOWN);
        assertThat(PkiTestSupport.policyOids(issued.certificate())).contains(KrPkiOids.GRADE_UNKNOWN);
    }

    @Test
    void unregistered_aaguid_yields_unknown_grade() throws Exception {
        KeyPair credential = PkiTestSupport.newCredentialKeyPair();
        var svc = service(new InMemoryAuthenticatorMetadataRegistry()); // 비어 있음
        byte[] unknownAaguid = {2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2};

        var issued = svc.bind(PkiTestSupport.verifiedRegistration(credential, unknownAaguid, CRED_ID), raA, "홍길동");

        assertThat(issued.grade()).isEqualTo(AuthenticatorGrade.UNKNOWN);
    }

    @Test
    void distinct_grades_map_to_distinct_policy_oids() {
        assertThat(Arrays.stream(AuthenticatorGrade.values()).map(AuthenticatorGrade::policyOid).distinct().count())
                .isEqualTo(AuthenticatorGrade.values().length);
    }
}
