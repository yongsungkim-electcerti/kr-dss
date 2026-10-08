package com.electcerti.krdss.dss.api;

/**
 * 서명 검증 최종 판정 — ETSI EN 319 102-1 정합.
 */
public enum VerificationStatus {
    /** 유효. */
    TOTAL_PASSED,
    /** 확인불가. */
    INDETERMINATE,
    /** 무효. */
    TOTAL_FAILED
}
