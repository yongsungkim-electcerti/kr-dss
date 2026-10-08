package com.electcerti.krdss.dss.pki;

import java.util.Objects;

/**
 * HSM Attestation Object (특허-B 청구항 10, 구조는 특허-C 발명 C-2 참조).
 *
 * <p>HSM 이 서명키 생성 시 내부 Attestation 키로 서명하여 생성하는 장치 신뢰 증명이다.
 * 특허-C 의 {@code HSMAttestationObject}/{@code keyGenStatement} 구조에 대응한다.</p>
 *
 * <p>본 PoC 모델은 발급 인프라(특허-B) 관점에서 필요한 필드만 담는다. {@code attestationSig}
 * 의 HSM Attestation Root CA 체인 검증(특허-C 발명 C-2)은 본 모듈 범위 밖이므로, 그 결과를
 * {@code signatureVerified} 로 전달받는다.</p>
 *
 * @param hsmDeviceId       HSM 장치 모델 식별자(특허-C HSM 서브목록 조회 키)
 * @param hsmInstanceId     HSM 인스턴스 고유 식별자(없으면 길이 0)
 * @param nonExtractable    서명키 비추출성 여부(keyGenStatement.nonExtractable)
 * @param securityLevel     보안 수준 표기(예: {@code EAL4+}, {@code FIPS-140-3-L3})
 * @param signatureVerified attestationSig 가 HSM Attestation Root CA 체인으로 검증되었는지(특허-C)
 */
public record HsmAttestation(
        byte[] hsmDeviceId,
        byte[] hsmInstanceId,
        boolean nonExtractable,
        String securityLevel,
        boolean signatureVerified) {

    public HsmAttestation {
        Objects.requireNonNull(hsmDeviceId, "hsmDeviceId");
        hsmDeviceId = hsmDeviceId.clone();
        hsmInstanceId = hsmInstanceId == null ? new byte[0] : hsmInstanceId.clone();
    }

    @Override
    public byte[] hsmDeviceId() {
        return hsmDeviceId.clone();
    }

    @Override
    public byte[] hsmInstanceId() {
        return hsmInstanceId.clone();
    }
}
