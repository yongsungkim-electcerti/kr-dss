package com.electcerti.krdss.dss.pki;

/**
 * WebAuthn Attestation Statement 의 암호학적 검증 결과 (특허-B 청구항 1·5).
 *
 * <p>Attestation Statement 자체의 검증(예: webauthn4j) 은 등록 절차(상위 계층)에서 수행하고,
 * 본 발급 인프라는 그 <b>결과</b>(검증 성공 여부·attestation 형식)를 입력으로 받아
 * 장치 보안 등급 판정에 반영한다.</p>
 *
 * @param verified attestation 이 암호학적으로 검증되었는지 여부
 * @param format   attestation 형식(예: {@code packed}, {@code tpm}, {@code none})
 */
public record AttestationVerificationResult(boolean verified, String format) {

    /** 검증된 attestation 결과. */
    public static AttestationVerificationResult verified(String format) {
        return new AttestationVerificationResult(true, format);
    }

    /** 미검증(self/none) attestation 결과. */
    public static AttestationVerificationResult unverified() {
        return new AttestationVerificationResult(false, "none");
    }
}
