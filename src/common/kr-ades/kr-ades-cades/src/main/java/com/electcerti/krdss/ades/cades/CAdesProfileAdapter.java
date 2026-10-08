package com.electcerti.krdss.ades.cades;

import com.electcerti.krdss.ades.core.KrAdesFormat;
import com.electcerti.krdss.ades.core.KrAdesLevel;
import com.electcerti.krdss.ades.core.PackagingType;
import com.electcerti.krdss.ades.core.SignatureProfileAdapter;
import eu.europa.esig.dss.asic.cades.ASiCWithCAdESSignatureParameters;
import eu.europa.esig.dss.cades.CAdESSignatureParameters;
import eu.europa.esig.dss.enumerations.ASiCContainerType;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;

/**
 * KR-CAdES 프로파일 어댑터 — ETSI EN 319 122 정합.
 */
public final class CAdesProfileAdapter implements SignatureProfileAdapter {

    /** KR-CAdES 프로파일 어댑터를 생성한다. */
    public CAdesProfileAdapter() {
    }

    @Override
    public KrAdesFormat format() {
        return KrAdesFormat.CADES;
    }

    @Override
    public boolean supports(KrAdesLevel level, PackagingType packaging) {
        return level != null && (packaging == PackagingType.DETACHED
                || packaging == PackagingType.ENVELOPING
                || packaging == PackagingType.ASIC);
    }

    /**
     * KR-AdES 레벨을 DSS CAdES baseline 프로파일로 변환한다.
     *
     * @param level KR-AdES 레벨
     * @return DSS CAdES baseline 서명 레벨
     */
    public SignatureLevel toDssSignatureLevel(KrAdesLevel level) {
        return switch (level) {
            case KR_B -> SignatureLevel.CAdES_BASELINE_B;
            case KR_T -> SignatureLevel.CAdES_BASELINE_T;
            case KR_LT -> SignatureLevel.CAdES_BASELINE_LT;
            case KR_LTA -> SignatureLevel.CAdES_BASELINE_LTA;
        };
    }

    /**
     * KR 패키징을 일반 CAdES DSS 패키징으로 변환한다.
     *
     * @param packaging KR-AdES 패키징
     * @return DSS 서명 패키징
     */
    public SignaturePackaging toDssSignaturePackaging(PackagingType packaging) {
        return switch (packaging) {
            case DETACHED -> SignaturePackaging.DETACHED;
            case ENVELOPING -> SignaturePackaging.ENVELOPING;
            case ENVELOPED, ASIC -> throw new IllegalArgumentException(
                    "CAdES DSS packaging requires DETACHED or ENVELOPING: " + packaging);
        };
    }

    /**
     * ETSI EN 319 122 기반 CAdES 서명 파라미터를 생성한다.
     *
     * @param level KR-AdES 레벨
     * @param packaging KR-AdES 패키징
     * @return DSS CAdES 서명 파라미터
     */
    public CAdESSignatureParameters newSignatureParameters(KrAdesLevel level, PackagingType packaging) {
        if (!supports(level, packaging) || packaging == PackagingType.ASIC) {
            throw new IllegalArgumentException("Unsupported CAdES level/packaging: " + level + "/" + packaging);
        }

        CAdESSignatureParameters parameters = new CAdESSignatureParameters();
        parameters.setEn319122(true);
        parameters.setSignatureLevel(toDssSignatureLevel(level));
        parameters.setSignaturePackaging(toDssSignaturePackaging(packaging));
        return parameters;
    }

    /**
     * ETSI EN 319 162 ASiC-with-CAdES 서명 파라미터를 생성한다.
     *
     * @param level KR-AdES 레벨
     * @return DSS ASiC-with-CAdES 서명 파라미터
     */
    public ASiCWithCAdESSignatureParameters newAsicSignatureParameters(KrAdesLevel level) {
        if (level == null) {
            throw new IllegalArgumentException("CAdES ASiC level is required");
        }

        ASiCWithCAdESSignatureParameters parameters = new ASiCWithCAdESSignatureParameters();
        parameters.setEn319122(true);
        parameters.setSignatureLevel(toDssSignatureLevel(level));
        parameters.setSignaturePackaging(SignaturePackaging.DETACHED);
        parameters.aSiC().setContainerType(ASiCContainerType.ASiC_E);
        return parameters;
    }
}
