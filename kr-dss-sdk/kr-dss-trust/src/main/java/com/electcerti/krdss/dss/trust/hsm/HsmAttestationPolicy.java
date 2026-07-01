package com.electcerti.krdss.dss.trust.hsm;

import java.time.Duration;
import java.util.Set;

/**
 * HSM Attestation 검증 정책 (특허-C 청구항 13).
 *
 * @param requireNonExtractable 키 비추출성 필수 여부(true 면 nonExtractable=false → 무효)
 * @param acceptableCcLevels    허용 CC 등급 집합(비어 있으면 등급 무관)
 * @param maxAge                Attestation timestamp 허용 최대 경과 시간
 */
public record HsmAttestationPolicy(boolean requireNonExtractable, Set<String> acceptableCcLevels, Duration maxAge) {

    public HsmAttestationPolicy {
        acceptableCcLevels = acceptableCcLevels == null ? Set.of() : Set.copyOf(acceptableCcLevels);
        maxAge = maxAge == null ? Duration.ofDays(3650) : maxAge;
    }

    /** 기본 정책 — 비추출성 필수, 등급 무관, 10년 이내. */
    public static HsmAttestationPolicy defaults() {
        return new HsmAttestationPolicy(true, Set.of(), Duration.ofDays(3650));
    }

    public boolean acceptsSecurityLevel(HsmSecurityLevel level) {
        return acceptableCcLevels.isEmpty() || acceptableCcLevels.contains(level.ccLevel());
    }
}
