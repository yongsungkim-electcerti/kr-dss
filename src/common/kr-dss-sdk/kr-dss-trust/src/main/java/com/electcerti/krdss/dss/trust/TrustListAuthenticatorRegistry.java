package com.electcerti.krdss.dss.trust;

import com.electcerti.krdss.dss.pki.AuthenticatorGrade;
import com.electcerti.krdss.dss.pki.AuthenticatorMetadataRegistry;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;

import java.util.Objects;

/**
 * KR-TL WebAuthn 인증기 서브목록 기반 인증기 메타데이터 레지스트리.
 *
 * <p>특허-B {@link AuthenticatorMetadataRegistry}(AAGUID→등급)의 KR-TL 실구현이다. 특허-B 가
 * InMemory 스텁으로 두었던 등급 조회를, 특허-C 의 Layer 2 신뢰목록(FIDO MDS 준용)으로 연결한다.
 * GRANTED 상태가 아니면 {@link AuthenticatorGrade#UNKNOWN} 으로 판정한다.</p>
 */
public final class TrustListAuthenticatorRegistry implements AuthenticatorMetadataRegistry {

    private final DeviceTrustList deviceList;

    public TrustListAuthenticatorRegistry(DeviceTrustList deviceList) {
        this.deviceList = Objects.requireNonNull(deviceList, "deviceList");
    }

    @Override
    public AuthenticatorGrade gradeOf(byte[] aaguid) {
        return deviceList.findAuthenticator(aaguid)
                .filter(e -> e.status() == ServiceStatus.GRANTED)
                .map(AuthenticatorTrustEntry::grade)
                .orElse(AuthenticatorGrade.UNKNOWN);
    }
}
