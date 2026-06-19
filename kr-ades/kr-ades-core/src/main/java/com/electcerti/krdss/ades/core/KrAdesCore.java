package com.electcerti.krdss.ades.core;

import java.time.Instant;
import java.util.Map;

/**
 * KR-AdES-Core 공통 데이터 모델.
 *
 * <p>6종 프로파일이 공통으로 상속하는 핵심 필드 집합. JSON Schema / XML Schema 로
 * 정형화하며, 포맷별 프로파일이 {@link #extensionInfo()} 로 고유 확장 필드를 추가한다.</p>
 */
public record KrAdesCore(
        String profileId,
        KrAdesFormat format,
        KrAdesLevel level,
        PackagingType packaging,
        DocumentInfo documentInfo,
        Instant signingTime,
        Map<String, Object> extensionInfo
) {

    /** 서명 대상 문서 정보. */
    public record DocumentInfo(String name, String mimeType, byte[] digest, String digestAlgorithm) {
    }
}
