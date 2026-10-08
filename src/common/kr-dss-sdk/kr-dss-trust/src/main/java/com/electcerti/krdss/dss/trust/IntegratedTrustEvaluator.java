package com.electcerti.krdss.dss.trust;

import com.electcerti.krdss.dss.api.TrustListEvaluator;

import java.util.Objects;

/**
 * 특허-A 검증 라우터 ↔ 특허-C 통합 신뢰목록 어댑터 (A/B/C 연계).
 *
 * <p>kr-dss-api 의 {@link TrustListEvaluator} 계약을 구현하여, 라우터의 신뢰목록 hook 을
 * 특허-C {@link IntegratedTrustVerifier}(Layer1 신뢰서비스 + Layer2 장치 신뢰목록 이중검증)에
 * 연결한다.</p>
 */
public final class IntegratedTrustEvaluator implements TrustListEvaluator {

    private final IntegratedTrustVerifier verifier;

    public IntegratedTrustEvaluator(IntegratedTrustVerifier verifier) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    @Override
    public Evaluation evaluate(Query query) {
        DeviceType deviceType = query.deviceKind() == DeviceKind.HSM ? DeviceType.HSM : DeviceType.WEBAUTHN;
        IntegratedTrustVerifier.TrustVerdict verdict = verifier.verify(
                new IntegratedTrustVerifier.Request(query.caServiceId(), deviceType, query.deviceId()));
        return new Evaluation(verdict.status(), String.join(" · ", verdict.reasons()));
    }
}
