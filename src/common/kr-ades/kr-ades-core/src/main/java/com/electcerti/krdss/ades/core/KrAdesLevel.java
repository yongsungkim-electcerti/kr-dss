package com.electcerti.krdss.ades.core;

/**
 * KR-AdES 서명 레벨. 문서 보존 기간·법적 증거력 요구에 따라 단계화한다.
 */
public enum KrAdesLevel {

    /** 기본 서명. */
    KR_B("KR-B", "기본"),
    /** 시점보증(타임스탬프 포함). */
    KR_T("KR-T", "시점보증"),
    /** 장기검증(검증정보 포함). */
    KR_LT("KR-LT", "장기검증"),
    /** 장기보존(아카이브 타임스탬프). */
    KR_LTA("KR-LTA", "장기보존");

    private final String code;
    private final String description;

    KrAdesLevel(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String code() {
        return code;
    }

    public String description() {
        return description;
    }
}
