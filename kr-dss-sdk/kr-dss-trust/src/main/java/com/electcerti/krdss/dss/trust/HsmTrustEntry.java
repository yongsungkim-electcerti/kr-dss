package com.electcerti.krdss.dss.trust;

import com.electcerti.krdss.dss.pki.HsmGrade;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;

import java.security.cert.X509Certificate;
import java.util.Objects;

/**
 * HSM 장치 서브목록 항목 (특허-C 청구항 7).
 *
 * <p>HSM Attestation 으로 검증된 HSM 을 hsmDeviceId 단위로 등록한다.</p>
 *
 * @param hsmDeviceId              HSM 장치 모델 식별자
 * @param vendor                   제조사
 * @param model                    모델명
 * @param ccLevel                  CC EAL 등급(예: EAL4+)
 * @param fipsLevel                FIPS 등급(예: FIPS 140-3 L3)
 * @param attestationRootCertificate HSM Attestation Root 인증서(없으면 null)
 * @param grade                    보안 등급(특허-B HSM 등급 정책 연계)
 * @param status                   서비스 상태
 */
public record HsmTrustEntry(
        byte[] hsmDeviceId,
        String vendor,
        String model,
        String ccLevel,
        String fipsLevel,
        X509Certificate attestationRootCertificate,
        HsmGrade grade,
        ServiceStatus status) {

    public HsmTrustEntry {
        Objects.requireNonNull(hsmDeviceId, "hsmDeviceId");
        Objects.requireNonNull(grade, "grade");
        Objects.requireNonNull(status, "status");
        hsmDeviceId = hsmDeviceId.clone();
    }

    @Override
    public byte[] hsmDeviceId() {
        return hsmDeviceId.clone();
    }

    public HsmTrustEntry withGrade(HsmGrade newGrade) {
        return new HsmTrustEntry(hsmDeviceId, vendor, model, ccLevel, fipsLevel,
                attestationRootCertificate, newGrade, status);
    }

    public HsmTrustEntry withStatus(ServiceStatus newStatus) {
        return new HsmTrustEntry(hsmDeviceId, vendor, model, ccLevel, fipsLevel,
                attestationRootCertificate, grade, newStatus);
    }
}
