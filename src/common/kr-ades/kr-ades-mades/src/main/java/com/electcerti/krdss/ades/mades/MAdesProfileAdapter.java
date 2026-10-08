package com.electcerti.krdss.ades.mades;

import com.electcerti.krdss.ades.core.KrAdesFormat;
import com.electcerti.krdss.ades.core.KrAdesLevel;
import com.electcerti.krdss.ades.core.PackagingType;
import com.electcerti.krdss.ades.core.SignatureProfileAdapter;

/**
 * KR-MAdES 프로파일 어댑터 — Markdown(차세대 문서).
 *
 * <p>본문 무결성 보존을 위해 Detached/ASiC 패키징을 기본으로 한다.</p>
 */
public final class MAdesProfileAdapter implements SignatureProfileAdapter {

    @Override
    public KrAdesFormat format() {
        return KrAdesFormat.MADES;
    }

    @Override
    public boolean supports(KrAdesLevel level, PackagingType packaging) {
        return packaging == PackagingType.DETACHED || packaging == PackagingType.ASIC;
    }
}
