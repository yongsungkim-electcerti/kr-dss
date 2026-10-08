package com.electcerti.krdss.dss.pki;

import java.security.cert.X509Certificate;

/**
 * HSM 원격 서명 인증서 발급 결과 (특허-B 발명 B-4, 청구항 10·15).
 *
 * @param certificate      발급된 X.509 인증서
 * @param raId             발급 대상 RA 식별자
 * @param grade            판정된 HSM 보안 등급(청구항 15)
 * @param keyIdentifierHex SubjectKeyIdentifier(hex)
 * @param nonExtractable   서명키 비추출성 확인 여부
 */
public record HsmIssuedCertificate(
        X509Certificate certificate,
        String raId,
        HsmGrade grade,
        String keyIdentifierHex,
        boolean nonExtractable) {
}
