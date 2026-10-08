package com.electcerti.krdss.dss.pki;

import java.security.cert.X509Certificate;

/**
 * 단일 RA 에 대해 발급된 인증서와 그 결속 메타데이터.
 *
 * @param certificate      발급된 X.509 인증서
 * @param raId             발급 대상 RA 식별자
 * @param grade            판정된 장치 보안 등급(청구항 1·11)
 * @param keyIdentifierHex SubjectKeyIdentifier(hex) — 동일 등록 결과 연관 식별자(청구항 13)
 */
public record IssuedCertificate(
        X509Certificate certificate,
        String raId,
        AuthenticatorGrade grade,
        String keyIdentifierHex) {
}
