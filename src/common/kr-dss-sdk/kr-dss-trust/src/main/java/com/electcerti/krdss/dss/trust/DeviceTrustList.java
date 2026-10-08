package com.electcerti.krdss.dss.trust;

import com.electcerti.krdss.dss.pki.AuthenticatorGrade;
import com.electcerti.krdss.dss.pki.HsmGrade;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;

import java.util.HexFormat;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer 2 — 서명 생성 장치 신뢰목록 (특허-C 발명 C-1).
 *
 * <p>WebAuthn 인증기 서브목록(AAGUID)과 HSM 장치 서브목록(hsmDeviceId)을 통합 보유한다.
 * 정책 자동 갱신(청구항 8)에 의해 항목의 등급·상태가 갱신된다.</p>
 */
public final class DeviceTrustList {

    private final ConcurrentHashMap<String, AuthenticatorTrustEntry> authenticators = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HsmTrustEntry> hsms = new ConcurrentHashMap<>();

    // --- 등록 ---

    public DeviceTrustList registerAuthenticator(AuthenticatorTrustEntry entry) {
        authenticators.put(hex(entry.aaguid()), entry);
        return this;
    }

    public DeviceTrustList registerHsm(HsmTrustEntry entry) {
        hsms.put(hex(entry.hsmDeviceId()), entry);
        return this;
    }

    // --- 조회 ---

    public Optional<AuthenticatorTrustEntry> findAuthenticator(byte[] aaguid) {
        if (aaguid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(authenticators.get(hex(aaguid)));
    }

    public Optional<HsmTrustEntry> findHsm(byte[] hsmDeviceId) {
        if (hsmDeviceId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(hsms.get(hex(hsmDeviceId)));
    }

    // --- 정책 자동 갱신용 변경 (청구항 8) ---

    public void setAuthenticatorGrade(byte[] aaguid, AuthenticatorGrade grade) {
        authenticators.computeIfPresent(hex(aaguid), (k, e) -> e.withGrade(grade));
    }

    public void setAuthenticatorStatus(byte[] aaguid, ServiceStatus status) {
        authenticators.computeIfPresent(hex(aaguid), (k, e) -> e.withStatus(status));
    }

    public void setHsmGrade(byte[] hsmDeviceId, HsmGrade grade) {
        hsms.computeIfPresent(hex(hsmDeviceId), (k, e) -> e.withGrade(grade));
    }

    public void setHsmStatus(byte[] hsmDeviceId, ServiceStatus status) {
        hsms.computeIfPresent(hex(hsmDeviceId), (k, e) -> e.withStatus(status));
    }

    public int authenticatorCount() {
        return authenticators.size();
    }

    public int hsmCount() {
        return hsms.size();
    }

    private static String hex(byte[] id) {
        return HexFormat.of().formatHex(id);
    }
}
