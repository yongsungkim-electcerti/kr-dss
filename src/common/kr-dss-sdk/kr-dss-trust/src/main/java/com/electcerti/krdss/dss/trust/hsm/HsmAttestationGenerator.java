package com.electcerti.krdss.dss.trust.hsm;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;

/**
 * HSM Attestation 생성부 (특허-C 청구항 2).
 *
 * <p>HSM 이 서명키 생성 시, 장치 내부의 Attestation 키(장치 인증서에 대응하는 개인키)로
 * Attestation Object 의 TBS 를 서명해 {@link HsmAttestationObject} 를 생성한다(HSM 시뮬레이션).</p>
 */
public final class HsmAttestationGenerator {

    /**
     * HSM Attestation Object 를 생성·서명한다.
     *
     * @param hsmDeviceId       HSM 장치 모델 식별자
     * @param hsmInstanceId     HSM 인스턴스 식별자(없으면 null)
     * @param attestedPublicKey 생성된 서명키(피증명 키)의 공개키
     * @param keyGenStatement   서명키 생성 정책 진술
     * @param securityLevel     보안 수준
     * @param timestamp         키 생성 시각
     * @param attestationKey    장치 Attestation 키(개인키) — 이 키로 서명
     */
    public HsmAttestationObject generate(byte[] hsmDeviceId, byte[] hsmInstanceId,
                                         PublicKey attestedPublicKey, HsmKeyGenStatement keyGenStatement,
                                         HsmSecurityLevel securityLevel, Instant timestamp,
                                         PrivateKey attestationKey) {
        HsmAttestationObject unsigned = new HsmAttestationObject(1, hsmDeviceId, hsmInstanceId,
                attestedPublicKey, keyGenStatement, securityLevel, timestamp, new byte[0]);
        byte[] tbs = HsmAttestationCodec.tbsDer(unsigned);
        return unsigned.withSignature(sign(tbs, attestationKey));
    }

    private static byte[] sign(byte[] tbs, PrivateKey key) {
        try {
            String alg = key.getAlgorithm().startsWith("EC") ? "SHA256withECDSA" : "SHA256withRSA";
            Signature s = Signature.getInstance(alg);
            s.initSign(key);
            s.update(tbs);
            return s.sign();
        } catch (Exception e) {
            throw new IllegalStateException("HSM Attestation 서명 실패", e);
        }
    }
}
