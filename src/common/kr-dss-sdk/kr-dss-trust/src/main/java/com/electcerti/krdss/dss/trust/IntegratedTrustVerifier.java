package com.electcerti.krdss.dss.trust;

import com.electcerti.krdss.dss.api.VerificationStatus;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 통합 이중 신뢰 검증 (특허-C 청구항 1·3·12).
 *
 * <p>Layer 1(신뢰서비스 검증)과 Layer 2(서명 생성 장치 검증)를 단일 절차로 수행하고 결과를
 * 종합한다.</p>
 * <ul>
 *   <li><b>Layer 1</b>: 서명자 인증서 발급기관이 신뢰서비스 목록에 GRANTED 로 등재되었는지 확인.
 *       미등재·폐지 → {@code TOTAL_FAILED}(청구항 5).</li>
 *   <li><b>Layer 2</b>: 장치 식별자(AAGUID/hsmDeviceId)를 서브목록에서 조회. 미등재 → 정책에 따라
 *       INDETERMINATE/TOTAL_FAILED, 폐지 → TOTAL_FAILED, 정지 → INDETERMINATE, 등급 미달 →
 *       INDETERMINATE(청구항 12).</li>
 *   <li><b>종합</b>: 두 계층 중 가장 무거운 판정으로 종합(FAILED &gt; INDETERMINATE &gt; PASSED).</li>
 * </ul>
 */
public final class IntegratedTrustVerifier {

    private final KrIntegratedTrustList trustList;
    private final TrustPolicy policy;

    public IntegratedTrustVerifier(KrIntegratedTrustList trustList, TrustPolicy policy) {
        this.trustList = Objects.requireNonNull(trustList, "trustList");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /**
     * 통합 검증 요청.
     *
     * @param caServiceId 서명자 인증서 발급기관의 신뢰서비스 식별자(Layer 1 키)
     * @param deviceType  서명 생성 장치 유형
     * @param deviceId    장치 식별자(AAGUID 또는 hsmDeviceId)
     */
    public record Request(String caServiceId, DeviceType deviceType, byte[] deviceId) {
    }

    /** 통합 검증 결과. */
    public record TrustVerdict(VerificationStatus status, List<String> reasons) {
    }

    public TrustVerdict verify(Request req) {
        List<String> reasons = new ArrayList<>();
        VerificationStatus layer1 = verifyLayer1(req.caServiceId(), reasons);
        // Layer 1 에서 TOTAL_FAILED 면 단락(서비스 미공인은 곧바로 무효).
        if (layer1 == VerificationStatus.TOTAL_FAILED) {
            return new TrustVerdict(VerificationStatus.TOTAL_FAILED, reasons);
        }
        VerificationStatus layer2 = verifyLayer2(req.deviceType(), req.deviceId(), reasons);
        return new TrustVerdict(combine(layer1, layer2), reasons);
    }

    private VerificationStatus verifyLayer1(String caServiceId, List<String> reasons) {
        var entry = trustList.serviceList().find(caServiceId);
        if (entry.isEmpty()) {
            reasons.add("Layer1: 발급기관 미등재 — " + caServiceId);
            return VerificationStatus.TOTAL_FAILED;
        }
        ServiceStatus status = entry.get().status();
        if (status != ServiceStatus.GRANTED) {
            reasons.add("Layer1: 발급기관 비활성(" + status + ") — " + caServiceId);
            return VerificationStatus.TOTAL_FAILED;
        }
        reasons.add("Layer1: 발급기관 공인 확인 — " + caServiceId);
        return VerificationStatus.TOTAL_PASSED;
    }

    private VerificationStatus verifyLayer2(DeviceType deviceType, byte[] deviceId, List<String> reasons) {
        return switch (deviceType) {
            case WEBAUTHN -> verifyAuthenticator(deviceId, reasons);
            case HSM -> verifyHsm(deviceId, reasons);
        };
    }

    private VerificationStatus verifyAuthenticator(byte[] aaguid, List<String> reasons) {
        var found = trustList.deviceList().findAuthenticator(aaguid);
        if (found.isEmpty()) {
            reasons.add("Layer2: 미등재 인증기(AAGUID) → " + policy.unregisteredDevicePolicy());
            return policy.unregisteredDevicePolicy();
        }
        AuthenticatorTrustEntry e = found.get();
        if (e.status() == ServiceStatus.WITHDRAWN) {
            reasons.add("Layer2: 폐지된 인증기 → TOTAL_FAILED");
            return VerificationStatus.TOTAL_FAILED;
        }
        if (e.status() == ServiceStatus.SUSPENDED) {
            reasons.add("Layer2: 정지된 인증기 → INDETERMINATE");
            return VerificationStatus.INDETERMINATE;
        }
        if (!policy.meetsAuthenticatorGrade(e.grade())) {
            reasons.add("Layer2: 인증기 등급 미달(" + e.grade() + ") → INDETERMINATE");
            return VerificationStatus.INDETERMINATE;
        }
        reasons.add("Layer2: 인증기 공인·등급 충족(" + e.grade() + ")");
        return VerificationStatus.TOTAL_PASSED;
    }

    private VerificationStatus verifyHsm(byte[] hsmDeviceId, List<String> reasons) {
        var found = trustList.deviceList().findHsm(hsmDeviceId);
        if (found.isEmpty()) {
            reasons.add("Layer2: 미등재 HSM(hsmDeviceId) → " + policy.unregisteredDevicePolicy());
            return policy.unregisteredDevicePolicy();
        }
        HsmTrustEntry e = found.get();
        if (e.status() == ServiceStatus.WITHDRAWN) {
            reasons.add("Layer2: 폐지된 HSM → TOTAL_FAILED");
            return VerificationStatus.TOTAL_FAILED;
        }
        if (e.status() == ServiceStatus.SUSPENDED) {
            reasons.add("Layer2: 정지된 HSM → INDETERMINATE");
            return VerificationStatus.INDETERMINATE;
        }
        if (!policy.meetsHsmGrade(e.grade())) {
            reasons.add("Layer2: HSM 등급 미달(" + e.grade() + ") → INDETERMINATE");
            return VerificationStatus.INDETERMINATE;
        }
        reasons.add("Layer2: HSM 공인·등급 충족(" + e.grade() + ")");
        return VerificationStatus.TOTAL_PASSED;
    }

    /** 두 계층 결과 종합 — 가장 무거운 판정 우선(FAILED &gt; INDETERMINATE &gt; PASSED). */
    private static VerificationStatus combine(VerificationStatus a, VerificationStatus b) {
        if (a == VerificationStatus.TOTAL_FAILED || b == VerificationStatus.TOTAL_FAILED) {
            return VerificationStatus.TOTAL_FAILED;
        }
        if (a == VerificationStatus.INDETERMINATE || b == VerificationStatus.INDETERMINATE) {
            return VerificationStatus.INDETERMINATE;
        }
        return VerificationStatus.TOTAL_PASSED;
    }
}
