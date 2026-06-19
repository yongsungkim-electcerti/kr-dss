package com.electcerti.krdss.tl.builder;

import com.electcerti.krdss.tl.model.KrTrustList;

/**
 * KR-TL 생성 및 전자서명 책임.
 *
 * <p>운영 흐름: 인정사업자 제출 신뢰정보 → 수집·검토 → <b>KR-TL 생성·전자서명</b> → 배포.
 * 인정평가 결과를 TL 반영 기준 정보로 활용한다.</p>
 */
public final class KrTrustListBuilder {

    /** 수집·검토된 신뢰정보로부터 KR-TL 을 생성한다. */
    public KrTrustList build(KrTrustList draft) {
        // TODO: TS 119 612 스키마 직렬화.
        return draft;
    }

    /** 생성된 KR-TL 에 전자서명을 부여해 무결성·진본성을 보장한다. */
    public byte[] sign(KrTrustList trustList) {
        // TODO: BouncyCastle / DSS 로 XML 서명(Enveloped) 적용.
        throw new UnsupportedOperationException("KR-TL signing not yet implemented");
    }
}
