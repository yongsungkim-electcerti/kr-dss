package com.electcerti.krdss.dss.pki;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * 메모리 기반 HSM 장치 레지스트리 (특허-B 청구항 15의 PoC 구현).
 *
 * <p>hsmDeviceId(hex) → {@link HsmGrade} 매핑을 보유한다. 미등록 장치는 기본 등급
 * (기본값 {@link HsmGrade#UNKNOWN})으로 판정한다. 운영 환경에서는 KR-TL HSM 서브목록
 * 연동 구현(특허-C)으로 교체한다.</p>
 */
public final class InMemoryHsmDeviceRegistry implements HsmDeviceRegistry {

    private final Map<String, HsmGrade> byDeviceId = new HashMap<>();
    private final HsmGrade defaultGrade;

    public InMemoryHsmDeviceRegistry() {
        this(HsmGrade.UNKNOWN);
    }

    public InMemoryHsmDeviceRegistry(HsmGrade defaultGrade) {
        this.defaultGrade = defaultGrade;
    }

    /** hsmDeviceId 등급을 등록하고 자신을 반환한다(체이닝용). */
    public InMemoryHsmDeviceRegistry register(byte[] hsmDeviceId, HsmGrade grade) {
        byDeviceId.put(key(hsmDeviceId), grade);
        return this;
    }

    @Override
    public HsmGrade gradeOf(byte[] hsmDeviceId) {
        if (hsmDeviceId == null || hsmDeviceId.length == 0) {
            return defaultGrade;
        }
        return byDeviceId.getOrDefault(key(hsmDeviceId), defaultGrade);
    }

    private static String key(byte[] id) {
        return HexFormat.of().formatHex(id);
    }
}
