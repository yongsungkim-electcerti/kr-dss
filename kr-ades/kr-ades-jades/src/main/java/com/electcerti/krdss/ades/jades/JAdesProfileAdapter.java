package com.electcerti.krdss.ades.jades;

import com.electcerti.krdss.ades.core.KrAdesFormat;
import com.electcerti.krdss.ades.core.KrAdesLevel;
import com.electcerti.krdss.ades.core.PackagingType;
import com.electcerti.krdss.ades.core.SignatureProfileAdapter;

/**
 * KR-JAdES 프로파일 어댑터 — ETSI TS 119 182 정합.
 */
public final class JAdesProfileAdapter implements SignatureProfileAdapter {

    @Override
    public KrAdesFormat format() {
        return KrAdesFormat.JADES;
    }

    @Override
    public boolean supports(KrAdesLevel level, PackagingType packaging) {
        // TODO: DSS JAdESService 연동.
        return true;
    }
}
