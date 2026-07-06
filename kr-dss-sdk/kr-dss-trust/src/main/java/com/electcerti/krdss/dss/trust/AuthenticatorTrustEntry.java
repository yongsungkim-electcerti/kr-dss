package com.electcerti.krdss.dss.trust;

import com.electcerti.krdss.dss.pki.AuthenticatorGrade;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;

import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * WebAuthn 인증기 서브목록 항목 (특허-C 청구항 6·11).
 *
 * <p>FIDO Attestation 으로 검증된 인증기를 AAGUID 단위로 등록한다. 메타데이터 형식은 FIDO MDS
 * 구조를 준용한다(청구항 11).</p>
 *
 * @param aaguid                   인증기 모델 식별자
 * @param vendor                   제조사
 * @param model                    모델명
 * @param hardwareSecurityLevel    하드웨어 보안 수준(TEE/SE/SecureEnclave)
 * @param fidoLevel                FIDO 인증 등급(L1/L2/L3+)
 * @param attestationType          Attestation 유형(basic/attca/none 등)
 * @param attestationRootCertificate FIDO Attestation Root 인증서(없으면 null)
 * @param grade                    보안 등급(특허-B 등급 정책 연계)
 * @param status                   서비스 상태(GRANTED/WITHDRAWN/SUSPENDED)
 */
public record AuthenticatorTrustEntry(
        byte[] aaguid,
        String vendor,
        String model,
        String hardwareSecurityLevel,
        String fidoLevel,
        String attestationType,
        X509Certificate attestationRootCertificate,
        AuthenticatorGrade grade,
        ServiceStatus status) {

    public AuthenticatorTrustEntry {
        Objects.requireNonNull(aaguid, "aaguid");
        Objects.requireNonNull(grade, "grade");
        Objects.requireNonNull(status, "status");
        aaguid = aaguid.clone();
    }

    @Override
    public byte[] aaguid() {
        return aaguid.clone();
    }

    /** 등급만 교체한 새 항목(정책 자동 갱신용). */
    public AuthenticatorTrustEntry withGrade(AuthenticatorGrade newGrade) {
        return new AuthenticatorTrustEntry(aaguid, vendor, model, hardwareSecurityLevel,
                fidoLevel, attestationType, attestationRootCertificate, newGrade, status);
    }

    /** 상태만 교체한 새 항목(정책 자동 갱신용). */
    public AuthenticatorTrustEntry withStatus(ServiceStatus newStatus) {
        return new AuthenticatorTrustEntry(aaguid, vendor, model, hardwareSecurityLevel,
                fidoLevel, attestationType, attestationRootCertificate, grade, newStatus);
    }
}
