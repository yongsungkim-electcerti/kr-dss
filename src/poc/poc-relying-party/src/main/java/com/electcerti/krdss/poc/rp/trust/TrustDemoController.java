package com.electcerti.krdss.poc.rp.trust;

import com.electcerti.krdss.dss.trust.DeviceType;
import com.electcerti.krdss.dss.trust.KrIntegratedTrustList;
import com.electcerti.krdss.dss.trust.PolicyAutoUpdater;
import com.electcerti.krdss.dss.trust.TrustListUpdateEvent;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 특허-C 신뢰목록 데모 제어 API — {@code /api/trust/*} (A/B/C 연계 시연).
 *
 * <p>Mode 1 데모 인증기를 신뢰목록에서 폐지/복원하여, 동일 서명의 검증 결과가
 * TOTAL_PASSED ↔ TOTAL_FAILED 로 자동 전환됨을 보인다(정책 자동 갱신, 청구항 8).</p>
 */
@RestController
@RequestMapping("/api/trust")
public class TrustDemoController {

    private final PolicyAutoUpdater updater;
    private final KrIntegratedTrustList trustList;

    public TrustDemoController(PolicyAutoUpdater updater, KrIntegratedTrustList trustList) {
        this.updater = updater;
        this.trustList = trustList;
    }

    /** 데모 인증기 폐지 → 이후 검증 자동 TOTAL_FAILED. */
    @PostMapping("/device/revoke")
    public Map<String, Object> revoke() {
        updater.apply(TrustListUpdateEvent.deviceRevoked(DeviceType.WEBAUTHN, DemoTrustListConfig.DEMO_AAGUID));
        return status();
    }

    /** 데모 인증기 복원(등급 HIGH·GRANTED). */
    @PostMapping("/device/restore")
    public Map<String, Object> restore() {
        trustList.deviceList().setAuthenticatorStatus(DemoTrustListConfig.DEMO_AAGUID, ServiceStatus.GRANTED);
        return status();
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        var entry = trustList.deviceList().findAuthenticator(DemoTrustListConfig.DEMO_AAGUID);
        return Map.of(
                "operator", trustList.operatorAuthority(),
                "deviceStatus", entry.map(e -> e.status().name()).orElse("UNREGISTERED"),
                "deviceGrade", entry.map(e -> e.grade().name()).orElse("-"),
                "auditLog", updater.auditLog());
    }
}
