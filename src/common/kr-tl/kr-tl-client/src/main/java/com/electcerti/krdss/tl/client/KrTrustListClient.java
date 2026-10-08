package com.electcerti.krdss.tl.client;

import com.electcerti.krdss.tl.model.KrTrustList;

/**
 * 이용기관 측 KR-TL 조회·동기화 클라이언트.
 *
 * <p>SDK 의 {@code GetTrustList()/RefreshTrustList()} 가 본 클라이언트를 통해
 * 최신 신뢰목록을 확보하고 서명검증에 활용한다.</p>
 */
public interface KrTrustListClient {

    /** 캐시된 현재 신뢰목록을 반환한다. */
    KrTrustList current();

    /** 원본에서 최신 신뢰목록을 받아 동기화 후 반환한다. */
    KrTrustList refresh();
}
