package com.electcerti.krdss.dss.trust;

/**
 * KR-TL 갱신 이벤트 (특허-C 청구항 8, 발명 C-1).
 *
 * <p>정책 자동 갱신의 입력. 신규 장치 등재 / 장치 보안 등급 변경 / 장치 폐지 / 서비스 제공자
 * 폐지로 분류된다.</p>
 *
 * @param type        이벤트 유형
 * @param deviceType  장치 유형(장치 이벤트에 한함; TSP_REVOKED 시 null)
 * @param deviceId    장치 식별자(AAGUID/hsmDeviceId; TSP_REVOKED 시 null)
 * @param serviceId   신뢰서비스 식별자(TSP_REVOKED 시)
 * @param newGrade    변경 등급 이름(GRADE_CHANGED 시; enum name)
 * @param addedEntry  신규 등재 항목({@link AuthenticatorTrustEntry}/{@link HsmTrustEntry}; DEVICE_ADDED 시)
 */
public record TrustListUpdateEvent(
        Type type, DeviceType deviceType, byte[] deviceId,
        String serviceId, String newGrade, Object addedEntry) {

    public enum Type {
        /** 신규 장치 등재. */
        DEVICE_ADDED,
        /** 장치 보안 등급 변경. */
        GRADE_CHANGED,
        /** 장치 폐지. */
        DEVICE_REVOKED,
        /** 서비스 제공자(CA 등) 폐지. */
        TSP_REVOKED
    }

    public static TrustListUpdateEvent deviceAdded(DeviceType deviceType, Object entry) {
        return new TrustListUpdateEvent(Type.DEVICE_ADDED, deviceType, null, null, null, entry);
    }

    public static TrustListUpdateEvent gradeChanged(DeviceType deviceType, byte[] deviceId, String newGrade) {
        return new TrustListUpdateEvent(Type.GRADE_CHANGED, deviceType, deviceId, null, newGrade, null);
    }

    public static TrustListUpdateEvent deviceRevoked(DeviceType deviceType, byte[] deviceId) {
        return new TrustListUpdateEvent(Type.DEVICE_REVOKED, deviceType, deviceId, null, null, null);
    }

    public static TrustListUpdateEvent tspRevoked(String serviceId) {
        return new TrustListUpdateEvent(Type.TSP_REVOKED, null, null, serviceId, null, null);
    }
}
