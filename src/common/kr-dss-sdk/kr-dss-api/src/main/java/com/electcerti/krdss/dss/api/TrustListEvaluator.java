package com.electcerti.krdss.dss.api;

/**
 * 신뢰목록 평가 연계 계약 (특허-A 검증 라우터 ↔ 특허-C 통합 신뢰목록).
 *
 * <p>특허-A {@code VerificationRouter} 의 신뢰목록·폐지·장치신뢰 hook 이 이 인터페이스로
 * 특허-C 통합 이중검증(Layer1 신뢰서비스 + Layer2 장치 신뢰목록)을 호출한다. 의존성 역전을
 * 위해 계약을 최하위 모듈(kr-dss-api)에 두어, 라우터(kr-dss-core)와 구현(kr-dss-trust)이
 * 서로를 직접 의존하지 않도록 한다.</p>
 */
public interface TrustListEvaluator {

    /** 서명 생성 장치 유형. */
    enum DeviceKind {
        WEBAUTHN, HSM
    }

    /**
     * 평가 질의.
     *
     * @param caServiceId 서명자 인증서 발급기관 식별자(Layer1 키, 예: 발급 CA CN)
     * @param deviceKind  서명 생성 장치 유형
     * @param deviceId    장치 식별자(AAGUID 또는 hsmDeviceId)
     */
    record Query(String caServiceId, DeviceKind deviceKind, byte[] deviceId) {
    }

    /**
     * 평가 결과.
     *
     * @param status 3분류 판정(신뢰서비스+장치 이중검증 종합)
     * @param detail 사유 요약
     */
    record Evaluation(VerificationStatus status, String detail) {
    }

    /** 신뢰목록을 참조해 발급기관·장치의 공인 여부·등급을 평가한다. */
    Evaluation evaluate(Query query);
}
