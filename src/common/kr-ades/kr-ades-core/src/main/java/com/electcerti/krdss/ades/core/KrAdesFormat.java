package com.electcerti.krdss.ades.core;

/**
 * KR-AdES 6종 서명 포맷.
 *
 * <p>각 포맷은 {@link KrAdesCore} 공통 데이터 모델을 상속하여 포맷 고유 영역만 확장한다.</p>
 */
public enum KrAdesFormat {

    /** XML 서명 — ETSI EN 319 132 정합. */
    XADES("KR-XAdES", "XML"),
    /** CMS 서명 — ETSI EN 319 122 정합. */
    CADES("KR-CAdES", "CMS"),
    /** PDF 서명 — ETSI EN 319 142 정합. */
    PADES("KR-PAdES", "PDF"),
    /** JSON 서명 — ETSI TS 119 182 정합. */
    JADES("KR-JAdES", "JSON"),
    /** HWP 서명 — 국내 특화. */
    HADES("KR-HAdES", "HWP"),
    /** Markdown 서명 — 차세대 문서 대응. */
    MADES("KR-MAdES", "Markdown");

    private final String profileName;
    private final String documentType;

    KrAdesFormat(String profileName, String documentType) {
        this.profileName = profileName;
        this.documentType = documentType;
    }

    public String profileName() {
        return profileName;
    }

    public String documentType() {
        return documentType;
    }
}
