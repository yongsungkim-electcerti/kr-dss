package com.electcerti.krdss.dss.pki;

import java.security.PublicKey;
import java.util.Objects;

/**
 * WebAuthn 등록 결과 (특허-B 청구항 5).
 *
 * <p>Registration Binding 의 대상은 단순 공개키가 아니라 등록 과정 전체의 결과이다.
 * 인증기가 생성한 {@code credentialPublicKey}, 인증기 모델 식별자 {@code aaguid},
 * 인증기 제조사의 신뢰 증명 검증 결과 {@code attestation}, 그리고 자격증명 식별자
 * {@code credentialId} 를 포함한다.</p>
 *
 * @param credentialPublicKey 인증기가 생성한 공개키(= X.509 SubjectPublicKeyInfo)
 * @param aaguid              인증기 모델 식별자(없으면 길이 0)
 * @param credentialId        자격증명 식별자(SubjectKeyIdentifier 연관 식별 근거)
 * @param coseAlg             COSE 서명 알고리즘 식별자(예: -7 = ES256)
 * @param attestation         Attestation Statement 검증 결과
 */
public record RegistrationResult(
        PublicKey credentialPublicKey,
        byte[] aaguid,
        byte[] credentialId,
        int coseAlg,
        AttestationVerificationResult attestation) {

    public RegistrationResult {
        Objects.requireNonNull(credentialPublicKey, "credentialPublicKey");
        Objects.requireNonNull(credentialId, "credentialId");
        Objects.requireNonNull(attestation, "attestation");
        aaguid = aaguid == null ? new byte[0] : aaguid.clone();
        credentialId = credentialId.clone();
    }

    @Override
    public byte[] aaguid() {
        return aaguid.clone();
    }

    @Override
    public byte[] credentialId() {
        return credentialId.clone();
    }
}
