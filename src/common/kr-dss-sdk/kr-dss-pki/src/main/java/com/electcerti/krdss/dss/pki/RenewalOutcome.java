package com.electcerti.krdss.dss.pki;

/**
 * 인증서 갱신 결과 (특허-B 청구항 8, 발명 B-3).
 *
 * @param renewed       동일 SubjectPublicKeyInfo 로 발급된 갱신 인증서
 * @param previousGrade 갱신 전 장치 보안 등급
 * @param currentGrade  갱신 시 재판정된 장치 보안 등급
 * @param revokedOld    기존 인증서를 폐지했는지 여부
 */
public record RenewalOutcome(
        IssuedCertificate renewed,
        AuthenticatorGrade previousGrade,
        AuthenticatorGrade currentGrade,
        boolean revokedOld) {

    /** 갱신 시 AAGUID·Attestation 재검증 결과 등급이 변동되었는지(청구항 8). */
    public boolean gradeChanged() {
        return previousGrade != currentGrade;
    }
}
