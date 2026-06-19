package com.electcerti.krdss.ades.cades;

import com.electcerti.krdss.ades.core.KrAdesFormat;
import com.electcerti.krdss.ades.core.KrAdesLevel;
import com.electcerti.krdss.ades.core.PackagingType;
import com.electcerti.krdss.ades.core.SignatureProfileAdapter;

/**
 * KR-CAdES 프로파일 어댑터 — ETSI EN 319 122 정합.
 */
public final class CAdesProfileAdapter implements SignatureProfileAdapter {

    @Override
    public KrAdesFormat format() {
        return KrAdesFormat.CADES;
    }

    @Override
    public boolean supports(KrAdesLevel level, PackagingType packaging) {
        // TODO: DSS CAdESService 연동.
        return true;
    }
}
