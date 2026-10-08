package com.electcerti.krdss.dss.trust;

import com.electcerti.krdss.dss.pki.AuthenticatorGrade;
import com.electcerti.krdss.dss.pki.HsmGrade;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 정책 자동 갱신(Policy Auto-Update) — 특허-C 청구항 8 (발명 C-1).
 *
 * <p>KR-TL 갱신 이벤트를 분류하여 신뢰목록 상태를 자동 변경하고, 변경 이력을 감사 로그로
 * 남긴다. 검증기는 항상 현재 신뢰목록 상태를 참조하므로, 수동 정책 변경 없이 갱신이 즉시
 * 검증에 반영된다.</p>
 * <ul>
 *   <li><b>DEVICE_ADDED</b>: 신규 장치 등재 → 해당 유형 검증 허용.</li>
 *   <li><b>GRADE_CHANGED</b>: 등급 변경 → 허용 등급 자동 갱신.</li>
 *   <li><b>DEVICE_REVOKED</b>: 장치 폐지 → 해당 장치 서명 자동 TOTAL_FAILED(WITHDRAWN).</li>
 *   <li><b>TSP_REVOKED</b>: 서비스 제공자 폐지 → 해당 CA 발급 인증서 자동 TOTAL_FAILED.</li>
 * </ul>
 */
public final class PolicyAutoUpdater {

    /** 정책 변경 이력(감사 로그) 항목. */
    public record PolicyChangeRecord(String eventType, String target, String detail) {
    }

    private final KrIntegratedTrustList trustList;
    private final List<PolicyChangeRecord> auditLog = new CopyOnWriteArrayList<>();

    public PolicyAutoUpdater(KrIntegratedTrustList trustList) {
        this.trustList = Objects.requireNonNull(trustList, "trustList");
    }

    /** 갱신 이벤트를 적용하고 감사 로그를 남긴다. */
    public void apply(TrustListUpdateEvent event) {
        switch (event.type()) {
            case DEVICE_ADDED -> applyAdded(event);
            case GRADE_CHANGED -> applyGradeChanged(event);
            case DEVICE_REVOKED -> applyRevoked(event);
            case TSP_REVOKED -> applyTspRevoked(event);
        }
    }

    private void applyAdded(TrustListUpdateEvent event) {
        if (event.addedEntry() instanceof AuthenticatorTrustEntry a) {
            trustList.deviceList().registerAuthenticator(a);
            log("DEVICE_ADDED", "WEBAUTHN", a.vendor() + " " + a.model() + " grade=" + a.grade());
        } else if (event.addedEntry() instanceof HsmTrustEntry h) {
            trustList.deviceList().registerHsm(h);
            log("DEVICE_ADDED", "HSM", h.vendor() + " " + h.model() + " grade=" + h.grade());
        } else {
            throw new IllegalArgumentException("DEVICE_ADDED 이벤트에 등재 항목 누락");
        }
    }

    private void applyGradeChanged(TrustListUpdateEvent event) {
        if (event.deviceType() == DeviceType.WEBAUTHN) {
            trustList.deviceList().setAuthenticatorGrade(
                    event.deviceId(), AuthenticatorGrade.valueOf(event.newGrade()));
        } else {
            trustList.deviceList().setHsmGrade(event.deviceId(), HsmGrade.valueOf(event.newGrade()));
        }
        log("GRADE_CHANGED", String.valueOf(event.deviceType()), "→ " + event.newGrade());
    }

    private void applyRevoked(TrustListUpdateEvent event) {
        if (event.deviceType() == DeviceType.WEBAUTHN) {
            trustList.deviceList().setAuthenticatorStatus(event.deviceId(), ServiceStatus.WITHDRAWN);
        } else {
            trustList.deviceList().setHsmStatus(event.deviceId(), ServiceStatus.WITHDRAWN);
        }
        log("DEVICE_REVOKED", String.valueOf(event.deviceType()), "→ WITHDRAWN(TOTAL_FAILED)");
    }

    private void applyTspRevoked(TrustListUpdateEvent event) {
        trustList.serviceList().setStatus(event.serviceId(), ServiceStatus.WITHDRAWN);
        log("TSP_REVOKED", event.serviceId(), "→ WITHDRAWN(TOTAL_FAILED)");
    }

    private void log(String eventType, String target, String detail) {
        auditLog.add(new PolicyChangeRecord(eventType, target, detail));
    }

    /** 정책 변경 감사 로그(불변 스냅샷). */
    public List<PolicyChangeRecord> auditLog() {
        return List.copyOf(auditLog);
    }
}
