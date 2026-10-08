package com.electcerti.krdss.dss.core.verify;

import com.electcerti.krdss.dss.api.VerificationStatus;

import java.util.List;

/**
 * 정책 기반 검증 라우터의 통합 검증 결과(특허-A 청구항 14).
 *
 * @param indication    TOTAL_PASSED / INDETERMINATE / TOTAL_FAILED
 * @param subIndication 상세 사유(실패/확인불가 시, 성공 시 null)
 * @param signaturePath 선택된 검증 경로(WEBAUTHN/HSM/STANDARD)
 * @param signerSubject 서명자 식별명(가능 시)
 * @param signingTime   서명 시각(가능 시)
 * @param checks        단계별 점검 근거
 */
public record VerificationResult(
        VerificationStatus indication,
        String subIndication,
        String signaturePath,
        String signerSubject,
        String signingTime,
        List<Check> checks) {

    /** 개별 점검 결과. */
    public record Check(String name, boolean passed, String message) {
    }
}
