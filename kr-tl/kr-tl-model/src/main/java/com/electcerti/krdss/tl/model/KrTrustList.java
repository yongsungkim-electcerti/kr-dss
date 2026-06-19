package com.electcerti.krdss.tl.model;

import java.time.Instant;
import java.util.List;

/**
 * KR-TL(한국형 신뢰목록) 도메인 모델 — ETSI TS 119 612 정합.
 *
 * <p>EU TL 골격을 채택하되 국내 인정제도·인증서 유형을 반영한 확장 필드를 포함한다.
 * 신뢰목록 자체에 전자서명을 부여해 배포본의 무결성·진본성을 보장한다.</p>
 */
public record KrTrustList(
        SchemeInformation schemeInformation,
        List<TrustServiceProvider> trustServiceProviders
) {

    /** Scheme Information — 버전·발행정보. */
    public record SchemeInformation(int version, String operatorName, Instant issueDate, Instant nextUpdate) {
    }

    /** Trust Service Provider — 인정사업자 정보. */
    public record TrustServiceProvider(String name, List<TrustService> services) {
    }

    /** 개별 신뢰 서비스(RootCA·OCSP·TSA 등). */
    public record TrustService(
            String serviceTypeIdentifier,
            String serviceName,
            ServiceStatus status,
            Instant statusStartingTime,
            byte[] digitalIdentity
    ) {
    }

    /** 서비스 상태. */
    public enum ServiceStatus {
        GRANTED,
        WITHDRAWN,
        SUSPENDED
    }
}
