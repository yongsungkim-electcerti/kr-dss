package com.electcerti.krdss.dss.trust;

import com.electcerti.krdss.dss.pki.HsmDeviceRegistry;
import com.electcerti.krdss.dss.pki.HsmGrade;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;

import java.util.Objects;

/**
 * KR-TL HSM 장치 서브목록 기반 HSM 레지스트리.
 *
 * <p>특허-B {@link HsmDeviceRegistry}(hsmDeviceId→등급)의 KR-TL 실구현이다. GRANTED 상태가
 * 아니면 {@link HsmGrade#UNKNOWN} 으로 판정한다.</p>
 */
public final class TrustListHsmRegistry implements HsmDeviceRegistry {

    private final DeviceTrustList deviceList;

    public TrustListHsmRegistry(DeviceTrustList deviceList) {
        this.deviceList = Objects.requireNonNull(deviceList, "deviceList");
    }

    @Override
    public HsmGrade gradeOf(byte[] hsmDeviceId) {
        return deviceList.findHsm(hsmDeviceId)
                .filter(e -> e.status() == ServiceStatus.GRANTED)
                .map(HsmTrustEntry::grade)
                .orElse(HsmGrade.UNKNOWN);
    }
}
