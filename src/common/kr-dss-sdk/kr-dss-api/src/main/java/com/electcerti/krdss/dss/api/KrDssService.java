package com.electcerti.krdss.dss.api;

import com.electcerti.krdss.dss.api.Dtos.ExtractRequest;
import com.electcerti.krdss.dss.api.Dtos.ExtractResult;
import com.electcerti.krdss.dss.api.Dtos.SignRequest;
import com.electcerti.krdss.dss.api.Dtos.SignResult;
import com.electcerti.krdss.dss.api.Dtos.TimestampRequest;
import com.electcerti.krdss.dss.api.Dtos.TimestampResult;
import com.electcerti.krdss.dss.api.Dtos.VerifyRequest;
import com.electcerti.krdss.dss.api.Dtos.VerifyResult;
import com.electcerti.krdss.tl.model.KrTrustList;

/**
 * KR-DSS SDK 공통 진입점.
 *
 * <p>전자서명 전 과정(생성·추출·검증·신뢰연동·타임스탬프)을 포맷에 독립적인
 * 단일 인터페이스로 제공한다. 문서편집기·뷰어·기안기·서명앱 등 다양한 이용환경에서
 * 동일하게 호출된다.</p>
 */
public interface KrDssService {

    /** 전자문서에 서명을 생성하고 패키징하여 서명문서를 반환한다. */
    SignResult sign(SignRequest request);

    /** 서명문서에서 서명·Manifest 등 검증 대상 정보를 추출한다. */
    ExtractResult extract(ExtractRequest request);

    /** 서명문서를 검증하고 판정·보고서를 반환한다. */
    VerifyResult verify(VerifyRequest request);

    /** 현재 신뢰목록(KR-TL)을 조회한다. */
    KrTrustList getTrustList();

    /** 신뢰목록(KR-TL)을 최신본으로 갱신한다. */
    KrTrustList refreshTrustList();

    /** 타임스탬프(시점보증)를 발급한다. */
    TimestampResult timestamp(TimestampRequest request);
}
