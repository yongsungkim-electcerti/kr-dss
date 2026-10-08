package com.electcerti.krdss.dss.trust.hsm;

/**
 * 서명키 생성 정책 진술 (특허-C 청구항 9 — {@code keyGenStatement}).
 *
 * @param algorithm      서명키 알고리즘(예: EC, RSA)
 * @param keySize        키 크기(비트)
 * @param nonExtractable 키 추출 불가 여부(발명 C-2 핵심)
 * @param keyUsage       키 용도(예: digitalSignature)
 */
public record HsmKeyGenStatement(String algorithm, int keySize, boolean nonExtractable, String keyUsage) {
}
