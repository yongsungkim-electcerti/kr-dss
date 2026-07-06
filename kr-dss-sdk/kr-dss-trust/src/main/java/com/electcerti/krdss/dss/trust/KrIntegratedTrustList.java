package com.electcerti.krdss.dss.trust;

import java.util.Objects;

/**
 * KR-TL — 전자서명 통합 신뢰목록 (특허-C 청구항 1).
 *
 * <p>Layer 1(신뢰서비스 목록)과 Layer 2(서명 생성 장치 신뢰목록)를 하나로 묶어, 신뢰서비스
 * 차원과 서명 생성 장치 차원의 이중 보증을 단일 검증 절차 내에서 수행하도록 한다. 신뢰목록은
 * 국가기관 또는 정부 위탁기관이 운영한다(청구항 14).</p>
 */
public final class KrIntegratedTrustList {

    private final TrustServiceRegistry serviceList;
    private final DeviceTrustList deviceList;
    private final String operatorAuthority;

    public KrIntegratedTrustList(TrustServiceRegistry serviceList, DeviceTrustList deviceList,
                                 String operatorAuthority) {
        this.serviceList = Objects.requireNonNull(serviceList, "serviceList");
        this.deviceList = Objects.requireNonNull(deviceList, "deviceList");
        this.operatorAuthority = operatorAuthority == null ? "KISA" : operatorAuthority;
    }

    /** Layer 1 — 신뢰서비스 목록. */
    public TrustServiceRegistry serviceList() {
        return serviceList;
    }

    /** Layer 2 — 서명 생성 장치 신뢰목록. */
    public DeviceTrustList deviceList() {
        return deviceList;
    }

    /** 신뢰목록 운영 주체(청구항 14). */
    public String operatorAuthority() {
        return operatorAuthority;
    }
}
