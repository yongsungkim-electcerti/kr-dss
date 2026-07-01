package com.electcerti.krdss.dss.trust;

import com.electcerti.krdss.dss.api.VerificationStatus;
import com.electcerti.krdss.dss.pki.AuthenticatorGrade;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 특허-C 청구항 8 — 정책 자동 갱신(Policy Auto-Update).
 */
class PolicyAutoUpdaterTest {

    private IntegratedTrustVerifier.Request webauthn(byte[] aaguid) {
        return new IntegratedTrustVerifier.Request(TrustTestSupport.CA_SERVICE, DeviceType.WEBAUTHN, aaguid);
    }

    @Test
    void device_revoked_event_makes_verification_total_failed() {
        var tl = TrustTestSupport.trustList();
        var verifier = new IntegratedTrustVerifier(tl, new TrustPolicy());
        var updater = new PolicyAutoUpdater(tl);

        assertThat(verifier.verify(webauthn(TrustTestSupport.AAGUID_HIGH)).status())
                .isEqualTo(VerificationStatus.TOTAL_PASSED);

        // 청구항 8: 장치 폐지 이벤트 → 즉시 자동 TOTAL_FAILED
        updater.apply(TrustListUpdateEvent.deviceRevoked(DeviceType.WEBAUTHN, TrustTestSupport.AAGUID_HIGH));

        assertThat(verifier.verify(webauthn(TrustTestSupport.AAGUID_HIGH)).status())
                .isEqualTo(VerificationStatus.TOTAL_FAILED);
        assertThat(updater.auditLog()).anyMatch(r -> r.eventType().equals("DEVICE_REVOKED"));
    }

    @Test
    void grade_changed_event_updates_verification() {
        var tl = TrustTestSupport.trustList();
        var verifier = new IntegratedTrustVerifier(tl, new TrustPolicy());
        var updater = new PolicyAutoUpdater(tl);

        // HIGH → LOW 강등, 기본 정책 최소 MEDIUM → INDETERMINATE
        updater.apply(TrustListUpdateEvent.gradeChanged(
                DeviceType.WEBAUTHN, TrustTestSupport.AAGUID_HIGH, "LOW"));

        assertThat(verifier.verify(webauthn(TrustTestSupport.AAGUID_HIGH)).status())
                .isEqualTo(VerificationStatus.INDETERMINATE);
    }

    @Test
    void device_added_event_registers_and_allows() {
        var tl = TrustTestSupport.trustList();
        var verifier = new IntegratedTrustVerifier(tl, new TrustPolicy());
        var updater = new PolicyAutoUpdater(tl);
        byte[] fresh = {7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7};

        // 등재 전: 미등재 → INDETERMINATE
        assertThat(verifier.verify(webauthn(fresh)).status()).isEqualTo(VerificationStatus.INDETERMINATE);

        updater.apply(TrustListUpdateEvent.deviceAdded(DeviceType.WEBAUTHN, new AuthenticatorTrustEntry(
                fresh, "Feitian", "K9", "SE", "L2", "basic",
                null, AuthenticatorGrade.HIGH, ServiceStatus.GRANTED)));

        // 등재 후: 허용 → TOTAL_PASSED
        assertThat(verifier.verify(webauthn(fresh)).status()).isEqualTo(VerificationStatus.TOTAL_PASSED);
    }

    @Test
    void tsp_revoked_event_fails_layer1() {
        var tl = TrustTestSupport.trustList();
        var verifier = new IntegratedTrustVerifier(tl, new TrustPolicy());
        var updater = new PolicyAutoUpdater(tl);

        updater.apply(TrustListUpdateEvent.tspRevoked(TrustTestSupport.CA_SERVICE));

        // 발급기관 폐지 → Layer1 TOTAL_FAILED
        assertThat(verifier.verify(webauthn(TrustTestSupport.AAGUID_HIGH)).status())
                .isEqualTo(VerificationStatus.TOTAL_FAILED);
        assertThat(updater.auditLog()).anyMatch(r -> r.eventType().equals("TSP_REVOKED"));
    }
}
