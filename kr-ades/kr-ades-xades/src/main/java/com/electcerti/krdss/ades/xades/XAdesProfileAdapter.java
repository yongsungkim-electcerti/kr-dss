package com.electcerti.krdss.ades.xades;

import com.electcerti.krdss.ades.core.KrAdesFormat;
import com.electcerti.krdss.ades.core.KrAdesLevel;
import com.electcerti.krdss.ades.core.PackagingType;
import com.electcerti.krdss.ades.core.SignatureProfileAdapter;

/**
 * KR-XAdES 프로파일 어댑터 — ETSI EN 319 132 정합.
 */
public final class XAdesProfileAdapter implements SignatureProfileAdapter {

    @Override
    public KrAdesFormat format() {
        return KrAdesFormat.XADES;
    }

    @Override
    public boolean supports(KrAdesLevel level, PackagingType packaging) {
        // TODO: DSS XAdESService 연동 후 실제 지원 매트릭스로 교체.
        return true;
    }
}
