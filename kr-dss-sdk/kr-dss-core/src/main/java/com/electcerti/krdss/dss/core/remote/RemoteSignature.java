package com.electcerti.krdss.dss.core.remote;

/**
 * 원격 서명연산 결과.
 *
 * @param signatureValue     서명값(원시 바이트)
 * @param signingCertificate 서명 인증서(DER 인코딩) — 서명문서에 함께 포함되어 검증에 사용
 */
public record RemoteSignature(byte[] signatureValue, byte[] signingCertificate) {
}
