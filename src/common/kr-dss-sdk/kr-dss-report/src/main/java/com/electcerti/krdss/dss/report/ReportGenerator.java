package com.electcerti.krdss.dss.report;

/**
 * 검증 보고서 생성기.
 *
 * <p>특히 {@link ReportType#ETSI_VALIDATION_REPORT} 는 상호운용성·법적 분쟁·감사
 * 대응의 표준 근거가 된다.</p>
 */
public interface ReportGenerator {

    /** 검증 진단 데이터로부터 지정 형식의 보고서를 생성한다. */
    byte[] generate(ReportType type, Object diagnosticData);
}
