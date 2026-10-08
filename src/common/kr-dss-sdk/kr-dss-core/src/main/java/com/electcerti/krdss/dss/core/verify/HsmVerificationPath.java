package com.electcerti.krdss.dss.core.verify;

import com.electcerti.krdss.dss.api.VerificationStatus;
import com.electcerti.krdss.dss.core.remote.RemoteSignVerifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 정책 기반 검증 라우터 — HSM/표준 검증 경로(특허-A 청구항 13).
 *
 * <p>기존 {@link RemoteSignVerifier}(JSON 컨테이너, JDK 표준 RSA 검증)를 흡수하여
 * 라우터의 표준 결과 모델({@link VerificationResult})로 변환한다. HSM 원격 서명·표준
 * 전자서명은 동일 절차(서명검증 → 다이제스트 비교 → 인증서 유효기간)로 처리한다.</p>
 */
public class HsmVerificationPath {

    private final RemoteSignVerifier delegate = new RemoteSignVerifier();

    /** JSON 컨테이너를 검증한다. 원문 제공 시 무결성까지 확인한다. */
    public VerificationResult verify(byte[] signedObject, byte[] originalDocument) {
        RemoteSignVerifier.Report report = delegate.verify(signedObject, originalDocument);
        List<VerificationResult.Check> checks = new ArrayList<>();
        if (report.checks() != null) {
            for (RemoteSignVerifier.Check c : report.checks()) {
                checks.add(new VerificationResult.Check(c.name(), c.passed(), c.message()));
            }
        }
        return new VerificationResult(
                toStatus(report.indication()),
                report.subIndication(),
                "HSM",
                report.signerSubject(),
                report.signingTime(),
                checks);
    }

    private VerificationStatus toStatus(String indication) {
        return switch (indication == null ? "" : indication) {
            case "TOTAL_PASSED" -> VerificationStatus.TOTAL_PASSED;
            case "INDETERMINATE" -> VerificationStatus.INDETERMINATE;
            default -> VerificationStatus.TOTAL_FAILED;
        };
    }
}
