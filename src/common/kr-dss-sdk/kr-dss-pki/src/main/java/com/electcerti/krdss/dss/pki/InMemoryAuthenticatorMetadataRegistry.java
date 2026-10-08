package com.electcerti.krdss.dss.pki;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;

/**
 * 메모리 기반 인증기 메타데이터 레지스트리 (특허-B 청구항 12의 PoC 구현).
 *
 * <p>AAGUID(hex) → {@link AuthenticatorGrade} 매핑을 보유한다. 미등록 AAGUID 는
 * 생성자에 지정한 기본 등급(기본값 {@link AuthenticatorGrade#UNKNOWN})으로 판정한다.
 * 운영 환경에서는 FIDO MDS 연동 구현으로 교체할 수 있다(인터페이스 동일).</p>
 */
public final class InMemoryAuthenticatorMetadataRegistry implements AuthenticatorMetadataRegistry {

    private final Map<String, AuthenticatorGrade> byAaguid = new HashMap<>();
    private final AuthenticatorGrade defaultGrade;

    public InMemoryAuthenticatorMetadataRegistry() {
        this(AuthenticatorGrade.UNKNOWN);
    }

    public InMemoryAuthenticatorMetadataRegistry(AuthenticatorGrade defaultGrade) {
        this.defaultGrade = defaultGrade;
    }

    /** AAGUID 등급을 등록하고 자신을 반환한다(체이닝용). */
    public InMemoryAuthenticatorMetadataRegistry register(byte[] aaguid, AuthenticatorGrade grade) {
        byAaguid.put(key(aaguid), grade);
        return this;
    }

    @Override
    public AuthenticatorGrade gradeOf(byte[] aaguid) {
        if (aaguid == null || aaguid.length == 0) {
            return defaultGrade;
        }
        return byAaguid.getOrDefault(key(aaguid), defaultGrade);
    }

    private static String key(byte[] aaguid) {
        return HexFormat.of().formatHex(aaguid);
    }
}
