package com.electcerti.krdss.ades.hades;

import com.electcerti.krdss.ades.core.KrAdesFormat;
import com.electcerti.krdss.ades.core.KrAdesLevel;
import com.electcerti.krdss.ades.core.PackagingType;
import com.electcerti.krdss.ades.core.SignatureProfileAdapter;

/**
 * KR-HAdES 프로파일 어댑터 — HWP(국내 특화).
 *
 * <p>DSS 미지원 포맷으로, hwplib + BouncyCastle 기반 자체 구현이 필요하다.
 * Core 데이터 모델은 공통으로 상속하고 HWP 컨테이너 결합부만 확장한다.</p>
 */
public final class HAdesProfileAdapter implements SignatureProfileAdapter {

    @Override
    public KrAdesFormat format() {
        return KrAdesFormat.HADES;
    }

    @Override
    public boolean supports(KrAdesLevel level, PackagingType packaging) {
        // TODO: HWP 패키지 내 서명 스트림 결합 방식 확정 후 구현.
        return packaging == PackagingType.ENVELOPED;
    }
}
