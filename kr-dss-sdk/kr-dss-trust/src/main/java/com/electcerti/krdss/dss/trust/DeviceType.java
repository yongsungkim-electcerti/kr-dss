package com.electcerti.krdss.dss.trust;

/**
 * 서명 생성 장치 유형 (특허-C 발명 C-1 Layer 2).
 */
public enum DeviceType {
    /** WebAuthn 인증기(AAGUID, FIDO Attestation). */
    WEBAUTHN,
    /** 원격 서명 HSM(hsmDeviceId, HSM Attestation). */
    HSM
}
