package com.electcerti.krdss.dss.trust;

import com.electcerti.krdss.dss.api.VerificationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 특허-C 청구항 1·3·12 — 통합 이중 신뢰 검증.
 */
class IntegratedTrustVerifierTest {

    private IntegratedTrustVerifier verifier(KrIntegratedTrustList tl, TrustPolicy p) {
        return new IntegratedTrustVerifier(tl, p);
    }

    private IntegratedTrustVerifier.Request webauthn(byte[] aaguid) {
        return new IntegratedTrustVerifier.Request(TrustTestSupport.CA_SERVICE, DeviceType.WEBAUTHN, aaguid);
    }

    @Test
    void both_layers_pass_total_passed() {
        var v = verifier(TrustTestSupport.trustList(), new TrustPolicy());
        var verdict = v.verify(webauthn(TrustTestSupport.AAGUID_HIGH));
        assertThat(verdict.status()).isEqualTo(VerificationStatus.TOTAL_PASSED);
    }

    @Test
    void layer1_unregistered_ca_total_failed() {
        var v = verifier(TrustTestSupport.trustList(), new TrustPolicy());
        var verdict = v.verify(new IntegratedTrustVerifier.Request(
                "미등재CA", DeviceType.WEBAUTHN, TrustTestSupport.AAGUID_HIGH));
        assertThat(verdict.status()).isEqualTo(VerificationStatus.TOTAL_FAILED);
        assertThat(verdict.reasons()).anyMatch(r -> r.contains("발급기관 미등재"));
    }

    @Test
    void layer2_unregistered_device_follows_policy_indeterminate() {
        var v = verifier(TrustTestSupport.trustList(), new TrustPolicy());
        byte[] unknown = {9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9};
        assertThat(v.verify(webauthn(unknown)).status()).isEqualTo(VerificationStatus.INDETERMINATE);
    }

    @Test
    void layer2_unregistered_device_can_be_total_failed_by_policy() {
        var strict = new TrustPolicy(
                com.electcerti.krdss.dss.pki.AuthenticatorGrade.MEDIUM,
                com.electcerti.krdss.dss.pki.HsmGrade.MEDIUM,
                VerificationStatus.TOTAL_FAILED);
        var v = verifier(TrustTestSupport.trustList(), strict);
        byte[] unknown = {9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9, 9};
        assertThat(v.verify(webauthn(unknown)).status()).isEqualTo(VerificationStatus.TOTAL_FAILED);
    }

    @Test
    void layer2_grade_below_policy_indeterminate() {
        // AAGUID_LOW 는 등급 LOW, 기본 정책 최소 MEDIUM → 미달
        var v = verifier(TrustTestSupport.trustList(), new TrustPolicy());
        var verdict = v.verify(webauthn(TrustTestSupport.AAGUID_LOW));
        assertThat(verdict.status()).isEqualTo(VerificationStatus.INDETERMINATE);
        assertThat(verdict.reasons()).anyMatch(r -> r.contains("등급 미달"));
    }

    @Test
    void hsm_path_pass() {
        var v = verifier(TrustTestSupport.trustList(), new TrustPolicy());
        var verdict = v.verify(new IntegratedTrustVerifier.Request(
                TrustTestSupport.CA_SERVICE, DeviceType.HSM, TrustTestSupport.HSM_DEVICE));
        assertThat(verdict.status()).isEqualTo(VerificationStatus.TOTAL_PASSED);
    }
}
