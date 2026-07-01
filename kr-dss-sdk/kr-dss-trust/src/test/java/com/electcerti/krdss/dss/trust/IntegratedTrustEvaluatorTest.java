package com.electcerti.krdss.dss.trust;

import com.electcerti.krdss.dss.api.TrustListEvaluator;
import com.electcerti.krdss.dss.api.VerificationStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A/B/C 어댑터 — {@link IntegratedTrustEvaluator} 가 통합 이중검증을 kr-dss-api 계약으로 노출.
 */
class IntegratedTrustEvaluatorTest {

    private IntegratedTrustEvaluator evaluator() {
        var verifier = new IntegratedTrustVerifier(TrustTestSupport.trustList(), new TrustPolicy());
        return new IntegratedTrustEvaluator(verifier);
    }

    @Test
    void granted_high_device_passes() {
        var eval = evaluator().evaluate(new TrustListEvaluator.Query(
                TrustTestSupport.CA_SERVICE, TrustListEvaluator.DeviceKind.WEBAUTHN, TrustTestSupport.AAGUID_HIGH));
        assertThat(eval.status()).isEqualTo(VerificationStatus.TOTAL_PASSED);
    }

    @Test
    void unregistered_ca_fails() {
        var eval = evaluator().evaluate(new TrustListEvaluator.Query(
                "미등재CA", TrustListEvaluator.DeviceKind.WEBAUTHN, TrustTestSupport.AAGUID_HIGH));
        assertThat(eval.status()).isEqualTo(VerificationStatus.TOTAL_FAILED);
        assertThat(eval.detail()).contains("발급기관 미등재");
    }

    @Test
    void hsm_kind_maps_to_hsm_sublist() {
        var eval = evaluator().evaluate(new TrustListEvaluator.Query(
                TrustTestSupport.CA_SERVICE, TrustListEvaluator.DeviceKind.HSM, TrustTestSupport.HSM_DEVICE));
        assertThat(eval.status()).isEqualTo(VerificationStatus.TOTAL_PASSED);
    }
}
