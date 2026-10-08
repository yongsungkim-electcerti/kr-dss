package com.electcerti.krdss.dss.trust.hsm;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Objects;

/**
 * HSM Attestation Object (특허-C 청구항 9, 발명 C-2).
 *
 * <p>HSM 이 서명키 생성 시 내부 Attestation 키로 서명해 생성하는 장치 신뢰 증명. FIDO
 * Attestation Statement 의 HSM 적용.</p>
 *
 * @param version          버전(1)
 * @param hsmDeviceId      HSM 장치 모델 식별자
 * @param hsmInstanceId    HSM 인스턴스 식별자(없으면 길이 0)
 * @param hsmPublicKey     생성된 서명키(피증명 키)의 공개키
 * @param keyGenStatement  서명키 생성 정책 진술
 * @param securityLevel    보안 수준
 * @param timestamp        키 생성 시각
 * @param attestationSig   HSM Attestation 키(장치 인증서)의 서명값(미서명 시 길이 0)
 */
public record HsmAttestationObject(
        int version,
        byte[] hsmDeviceId,
        byte[] hsmInstanceId,
        PublicKey hsmPublicKey,
        HsmKeyGenStatement keyGenStatement,
        HsmSecurityLevel securityLevel,
        Instant timestamp,
        byte[] attestationSig) {

    public HsmAttestationObject {
        Objects.requireNonNull(hsmDeviceId, "hsmDeviceId");
        Objects.requireNonNull(hsmPublicKey, "hsmPublicKey");
        Objects.requireNonNull(keyGenStatement, "keyGenStatement");
        Objects.requireNonNull(securityLevel, "securityLevel");
        Objects.requireNonNull(timestamp, "timestamp");
        hsmDeviceId = hsmDeviceId.clone();
        hsmInstanceId = hsmInstanceId == null ? new byte[0] : hsmInstanceId.clone();
        attestationSig = attestationSig == null ? new byte[0] : attestationSig.clone();
    }

    @Override
    public byte[] hsmDeviceId() {
        return hsmDeviceId.clone();
    }

    @Override
    public byte[] hsmInstanceId() {
        return hsmInstanceId.clone();
    }

    @Override
    public byte[] attestationSig() {
        return attestationSig.clone();
    }

    /** attestationSig 만 채운 새 객체(생성부 서명 후). */
    public HsmAttestationObject withSignature(byte[] signature) {
        return new HsmAttestationObject(version, hsmDeviceId, hsmInstanceId, hsmPublicKey,
                keyGenStatement, securityLevel, timestamp, signature);
    }
}
