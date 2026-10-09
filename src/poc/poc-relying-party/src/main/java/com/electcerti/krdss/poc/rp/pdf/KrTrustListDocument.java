package com.electcerti.krdss.poc.rp.pdf;

import com.electcerti.krdss.tl.builder.KrTrustListXml;
import com.electcerti.krdss.tl.builder.SignedKrTrustList;
import com.electcerti.krdss.tl.model.KrTrustList;

/**
 * 검증 화면이 올린 KR-TL 배포본 — 형식을 가리지 않고 담는다.
 *
 * <p>같은 신뢰목록을 두 형식으로 낼 수 있다. XML(TS 119 612 + XAdES enveloped)이 표준 형식이고,
 * JSON(JWS)은 KR-TL 도메인 모델을 그대로 다루는 경량 형식이다. 서명 검증 방식이 서로 달라
 * ─ XML 은 EU DSS 문서 검증, JSON 은 JWS 서명값 확인 ─ 어느 쪽인지 구분해 둔다.</p>
 *
 * @param format    형식
 * @param trustList 신뢰목록 본문(형식과 무관하게 같은 모델)
 * @param raw       원본 바이트. XML 서명 검증은 이 바이트 위에서 수행한다
 * @param jws       JSON(JWS) 일 때의 판독·서명검증 결과. XML 이면 null
 */
public record KrTrustListDocument(Format format, KrTrustList trustList, byte[] raw, SignedKrTrustList jws) {

    /** 배포 형식. */
    public enum Format {
        /** TS 119 612 XML + XAdES enveloped 서명. */
        XML("XML (TS 119 612 / XAdES)"),
        /** KR-TL JSON + JWS 서명. */
        JSON_JWS("JSON (JWS)"),
        /** 서명 없는 KR-TL JSON. */
        JSON_PLAIN("JSON (미서명)");

        private final String label;

        Format(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * 업로드된 바이트를 형식에 맞게 읽는다.
     *
     * <p>XML 인지 JSON 인지는 첫 비공백 문자로 가른다. 확장자에 기대지 않는 편이
     * 화면에서 파일을 잘못 골랐을 때 메시지가 명확하다.</p>
     */
    public static KrTrustListDocument parse(byte[] document) {
        if (KrTrustListXml.looksLikeXml(document)) {
            // 기존 시연 파일은 이력 보존 기간 필드가 없어 명시적 호환 파서로 읽는다.
            return new KrTrustListDocument(Format.XML, KrTrustListXml.fromLegacyXml(document), document, null);
        }
        SignedKrTrustList jws = SignedKrTrustList.parse(document);
        return new KrTrustListDocument(
                jws.signed() ? Format.JSON_JWS : Format.JSON_PLAIN, jws.trustList(), document, jws);
    }
}
