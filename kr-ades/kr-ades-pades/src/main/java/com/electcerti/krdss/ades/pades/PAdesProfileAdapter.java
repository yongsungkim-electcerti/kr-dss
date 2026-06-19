package com.electcerti.krdss.ades.pades;

import com.electcerti.krdss.ades.core.KrAdesFormat;
import com.electcerti.krdss.ades.core.KrAdesLevel;
import com.electcerti.krdss.ades.core.PackagingType;
import com.electcerti.krdss.ades.core.SignatureProfileAdapter;

/**
 * KR-PAdES 프로파일 어댑터 — ETSI EN 319 142 정합.
 */
public final class PAdesProfileAdapter implements SignatureProfileAdapter {

    @Override
    public KrAdesFormat format() {
        return KrAdesFormat.PADES;
    }

    @Override
    public boolean supports(KrAdesLevel level, PackagingType packaging) {
        // PAdES 는 PDF 내장 서명이므로 ENVELOPED 만 의미를 가진다.
        return packaging == PackagingType.ENVELOPED;
    }
}
