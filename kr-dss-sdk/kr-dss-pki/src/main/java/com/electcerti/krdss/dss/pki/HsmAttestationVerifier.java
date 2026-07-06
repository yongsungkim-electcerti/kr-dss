package com.electcerti.krdss.dss.pki;

import java.util.Objects;

/**
 * HSM Attestation 검증부 (특허-B 청구항 10·15).
 *
 * <p>HSM Attestation Object 의 서명 검증 결과(특허-C 체인 검증)와 hsmDeviceId 메타데이터
 * 조회를 결합하여, 서명키가 HSM 내에서 생성·보호됨을 확인하고 보안 등급을 판정한다.</p>
 * <ul>
 *   <li>attestation 서명 미검증 → {@link HsmGrade#UNKNOWN}(미검증).</li>
 *   <li>비추출성({@code nonExtractable})이 보장되지 않으면 등급 상한을 LOW 로 제한한다(PoC 정책).</li>
 * </ul>
 */
public final class HsmAttestationVerifier {

    private final HsmDeviceRegistry registry;

    public HsmAttestationVerifier(HsmDeviceRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    public HsmAttestationResult verify(HsmAttestation attestation) {
        if (!attestation.signatureVerified()) {
            return new HsmAttestationResult(false, HsmGrade.UNKNOWN, attestation.nonExtractable());
        }
        HsmGrade grade = registry.gradeOf(attestation.hsmDeviceId());
        if (!attestation.nonExtractable() && (grade == HsmGrade.HIGH || grade == HsmGrade.MEDIUM)) {
            grade = HsmGrade.LOW; // 키 비추출 미보장 시 등급 상한 제한
        }
        return new HsmAttestationResult(true, grade, attestation.nonExtractable());
    }
}
