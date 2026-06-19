package com.electcerti.krdss.dss.core;

import com.electcerti.krdss.dss.api.Dtos.ExtractRequest;
import com.electcerti.krdss.dss.api.Dtos.ExtractResult;
import com.electcerti.krdss.dss.api.Dtos.SignRequest;
import com.electcerti.krdss.dss.api.Dtos.SignResult;
import com.electcerti.krdss.dss.api.Dtos.TimestampRequest;
import com.electcerti.krdss.dss.api.Dtos.TimestampResult;
import com.electcerti.krdss.dss.api.Dtos.VerifyRequest;
import com.electcerti.krdss.dss.api.Dtos.VerifyResult;
import com.electcerti.krdss.dss.api.KrDssService;
import com.electcerti.krdss.tl.model.KrTrustList;

/**
 * {@link KrDssService} 기본 구현 — 전 과정 오케스트레이션의 골격.
 *
 * <p>생성(A) 흐름: 서명 생성 → 패키징·문서 주입.<br>
 * 검증(B) 흐름: 추출 → KR-TL 조회 → 인증서·정책 검증 → 무결성 검증 → 판정·보고서.</p>
 */
public class KrDssServiceImpl implements KrDssService {

    @Override
    public SignResult sign(SignRequest request) {
        throw new UnsupportedOperationException("sign() not yet implemented");
    }

    @Override
    public ExtractResult extract(ExtractRequest request) {
        throw new UnsupportedOperationException("extract() not yet implemented");
    }

    @Override
    public VerifyResult verify(VerifyRequest request) {
        throw new UnsupportedOperationException("verify() not yet implemented");
    }

    @Override
    public KrTrustList getTrustList() {
        throw new UnsupportedOperationException("getTrustList() not yet implemented");
    }

    @Override
    public KrTrustList refreshTrustList() {
        throw new UnsupportedOperationException("refreshTrustList() not yet implemented");
    }

    @Override
    public TimestampResult timestamp(TimestampRequest request) {
        throw new UnsupportedOperationException("timestamp() not yet implemented");
    }
}
