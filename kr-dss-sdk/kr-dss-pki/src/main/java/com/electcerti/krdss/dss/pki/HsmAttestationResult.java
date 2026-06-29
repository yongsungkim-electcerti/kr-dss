package com.electcerti.krdss.dss.pki;

/**
 * HSM Attestation 검증 결과 (특허-B 청구항 10·15).
 *
 * @param verified       서명키가 HSM 내에서 생성·보호됨이 확인되었는지 여부
 * @param grade          판정된 HSM 보안 등급(정책 OID 차등 근거, 청구항 15)
 * @param nonExtractable 서명키 비추출성 확인 여부
 */
public record HsmAttestationResult(boolean verified, HsmGrade grade, boolean nonExtractable) {
}
