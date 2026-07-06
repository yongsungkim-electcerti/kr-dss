package com.electcerti.krdss.dss.pki;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 특허-B 발명 B-3 (청구항 8·9) — 인증서 생명주기 관리.
 */
class CertificateLifecycleManagerTest {

    private static final byte[] AAGUID = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    private static final byte[] CRED_ID = {7, 7, 7, 7};

    private final RegistrationAuthority raA = new RegistrationAuthority(
            "RA-A", "기관 A", RegistrationAuthority.RaType.IDENTITY, KrPkiOids.raPolicy("1"));
    private final RegistrationAuthority raB = new RegistrationAuthority(
            "RA-B", "기관 B", RegistrationAuthority.RaType.PLATFORM, KrPkiOids.raPolicy("2"));
    private final RegistrationAuthority raC = new RegistrationAuthority(
            "RA-C", "기관 C", RegistrationAuthority.RaType.IDENTITY, KrPkiOids.raPolicy("3"));

    private CertificateLifecycleManager newManager() {
        var ca = new CertificateAuthority("ca.kr-dss.example");
        var registry = new InMemoryAuthenticatorMetadataRegistry().register(AAGUID, AuthenticatorGrade.HIGH);
        return new CertificateLifecycleManager(new RegistrationBindingService(ca, registry));
    }

    private static BigInteger serial(IssuedCertificate ic) {
        return ic.certificate().getSerialNumber();
    }

    @Test
    void selective_revocation_does_not_affect_other_ra_certificates() throws Exception {
        KeyPair credential = PkiTestSupport.newCredentialKeyPair();
        var reg = PkiTestSupport.verifiedRegistration(credential, AAGUID, CRED_ID);
        var mgr = newManager();

        var certA = mgr.issue(reg, raA, "홍길동");
        var certB = mgr.issue(reg, raB, "홍길동");
        var certC = mgr.issue(reg, raC, "홍길동");

        // 청구항 9: RA-B 인증서만 폐지
        mgr.revoke(serial(certB));

        assertThat(mgr.status(serial(certB))).isEqualTo(CertificateStatus.REVOKED);
        assertThat(mgr.status(serial(certA))).isEqualTo(CertificateStatus.VALID);
        assertThat(mgr.status(serial(certC))).isEqualTo(CertificateStatus.VALID);
    }

    @Test
    void suspend_then_resume_roundtrip() throws Exception {
        KeyPair credential = PkiTestSupport.newCredentialKeyPair();
        var mgr = newManager();
        var cert = mgr.issue(PkiTestSupport.verifiedRegistration(credential, AAGUID, CRED_ID), raA, "홍길동");

        mgr.suspend(serial(cert));
        assertThat(mgr.status(serial(cert))).isEqualTo(CertificateStatus.SUSPENDED);

        mgr.resume(serial(cert));
        assertThat(mgr.status(serial(cert))).isEqualTo(CertificateStatus.VALID);
    }

    @Test
    void revoke_is_terminal_resume_is_rejected() throws Exception {
        KeyPair credential = PkiTestSupport.newCredentialKeyPair();
        var mgr = newManager();
        var cert = mgr.issue(PkiTestSupport.verifiedRegistration(credential, AAGUID, CRED_ID), raA, "홍길동");

        mgr.revoke(serial(cert));
        assertThatThrownBy(() -> mgr.resume(serial(cert))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> mgr.suspend(serial(cert))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void renewal_keeps_spki_and_redetermines_grade_and_policy() throws Exception {
        KeyPair credential = PkiTestSupport.newCredentialKeyPair();
        var mgr = newManager();
        var old = mgr.issue(PkiTestSupport.verifiedRegistration(credential, AAGUID, CRED_ID), raA, "홍길동");
        assertThat(old.grade()).isEqualTo(AuthenticatorGrade.HIGH);

        // 청구항 8: 갱신 시 Attestation 재검증 — 이번엔 미검증 → 등급 강등(HIGH → UNKNOWN)
        var freshReg = new RegistrationResult(credential.getPublic(), AAGUID, CRED_ID, -7,
                AttestationVerificationResult.unverified());
        var outcome = mgr.renew(old, freshReg, raA, "홍길동", true);

        // 동일 SubjectPublicKeyInfo (동일 Credential)
        assertThat(outcome.renewed().certificate().getPublicKey().getEncoded())
                .isEqualTo(old.certificate().getPublicKey().getEncoded());
        // 등급·정책 OID 재결정
        assertThat(outcome.gradeChanged()).isTrue();
        assertThat(outcome.previousGrade()).isEqualTo(AuthenticatorGrade.HIGH);
        assertThat(outcome.currentGrade()).isEqualTo(AuthenticatorGrade.UNKNOWN);
        assertThat(PkiTestSupport.policyOids(outcome.renewed().certificate())).contains(KrPkiOids.GRADE_UNKNOWN);
        // 기존 인증서 폐지, 갱신 인증서 유효
        assertThat(mgr.status(serial(old))).isEqualTo(CertificateStatus.REVOKED);
        assertThat(mgr.status(serial(outcome.renewed()))).isEqualTo(CertificateStatus.VALID);
    }

    @Test
    void renewal_can_keep_old_certificate_valid() throws Exception {
        KeyPair credential = PkiTestSupport.newCredentialKeyPair();
        var mgr = newManager();
        var old = mgr.issue(PkiTestSupport.verifiedRegistration(credential, AAGUID, CRED_ID), raA, "홍길동");

        var freshReg = PkiTestSupport.verifiedRegistration(credential, AAGUID, CRED_ID);
        var outcome = mgr.renew(old, freshReg, raA, "홍길동", false);

        assertThat(outcome.revokedOld()).isFalse();
        assertThat(outcome.gradeChanged()).isFalse();
        assertThat(mgr.status(serial(old))).isEqualTo(CertificateStatus.VALID);
        assertThat(mgr.status(serial(outcome.renewed()))).isEqualTo(CertificateStatus.VALID);
    }

    @Test
    void reissuance_with_new_credential_revokes_old_and_changes_key_identifier() throws Exception {
        KeyPair credential = PkiTestSupport.newCredentialKeyPair();
        var mgr = newManager();
        var old = mgr.issue(PkiTestSupport.verifiedRegistration(credential, AAGUID, CRED_ID), raA, "홍길동");

        // 신규 인증기 → 새 Credential 키쌍
        KeyPair newCredential = PkiTestSupport.newCredentialKeyPair();
        byte[] newCredId = {3, 3, 3, 3};
        var newReg = PkiTestSupport.verifiedRegistration(newCredential, AAGUID, newCredId);
        var fresh = mgr.reissue(old, newReg, raA, "홍길동");

        assertThat(mgr.status(serial(old))).isEqualTo(CertificateStatus.REVOKED);
        assertThat(mgr.status(serial(fresh))).isEqualTo(CertificateStatus.VALID);
        // 새 공개키 → 새 SubjectKeyIdentifier
        assertThat(fresh.keyIdentifierHex()).isNotEqualTo(old.keyIdentifierHex());
    }

    @Test
    void operations_on_untracked_serial_are_rejected() {
        var mgr = newManager();
        assertThatThrownBy(() -> mgr.revoke(BigInteger.valueOf(999)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(mgr.trackedSerials()).isEmpty();
    }
}
