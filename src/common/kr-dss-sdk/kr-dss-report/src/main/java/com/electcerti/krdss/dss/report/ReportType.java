package com.electcerti.krdss.dss.report;

/**
 * 검증 결과 보고서 4종.
 */
public enum ReportType {
    /** 원시 진단 데이터. */
    DIAGNOSTIC_DATA,
    /** ETSI 표준 검증보고서 — EN 319 102-1 · TS 119 102-2. */
    ETSI_VALIDATION_REPORT,
    /** 상세 보고서. */
    DETAILED_REPORT,
    /** 요약 보고서. */
    SIMPLE_REPORT
}
