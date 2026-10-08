package com.electcerti.krdss.dss.trust;

import com.electcerti.krdss.dss.pki.AuthenticatorGrade;
import com.electcerti.krdss.dss.pki.HsmGrade;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;

/**
 * kr-dss-trust 테스트 공용 헬퍼 — 신뢰목록 구성.
 */
final class TrustTestSupport {

    static final String CA_SERVICE = "공동인증CA";
    static final byte[] AAGUID_HIGH = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    static final byte[] AAGUID_LOW = {2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2};
    static final byte[] HSM_DEVICE = {10, 20, 30, 40};

    private TrustTestSupport() {
    }

    /** GRANTED CA(공동인증CA) + 인증기 2종(HIGH/LOW) + HSM(HIGH) 을 담은 통합 신뢰목록. */
    static KrIntegratedTrustList trustList() {
        var services = new TrustServiceRegistry().register(
                new TrustServiceRegistry.TrustServiceEntry(CA_SERVICE, "CA", ServiceStatus.GRANTED, "KISA"));

        var devices = new DeviceTrustList()
                .registerAuthenticator(new AuthenticatorTrustEntry(
                        AAGUID_HIGH, "YubiCo", "YubiKey5", "SE", "L2", "basic",
                        null, AuthenticatorGrade.HIGH, ServiceStatus.GRANTED))
                .registerAuthenticator(new AuthenticatorTrustEntry(
                        AAGUID_LOW, "Generic", "SoftKey", "SW", "L1", "none",
                        null, AuthenticatorGrade.LOW, ServiceStatus.GRANTED))
                .registerHsm(new HsmTrustEntry(
                        HSM_DEVICE, "Thales", "Luna7", "EAL4+", "FIPS-140-3-L3",
                        null, HsmGrade.HIGH, ServiceStatus.GRANTED));

        return new KrIntegratedTrustList(services, devices, "KISA");
    }
}
