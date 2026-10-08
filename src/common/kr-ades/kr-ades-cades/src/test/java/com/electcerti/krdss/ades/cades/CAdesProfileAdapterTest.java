package com.electcerti.krdss.ades.cades;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.electcerti.krdss.ades.core.KrAdesFormat;
import com.electcerti.krdss.ades.core.KrAdesLevel;
import com.electcerti.krdss.ades.core.PackagingType;
import eu.europa.esig.dss.cades.CAdESSignatureParameters;
import eu.europa.esig.dss.enumerations.ASiCContainerType;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import org.junit.jupiter.api.Test;

class CAdesProfileAdapterTest {

    private final CAdesProfileAdapter adapter = new CAdesProfileAdapter();

    @Test
    void exposesCadesFormat() {
        assertThat(adapter.format()).isEqualTo(KrAdesFormat.CADES);
    }

    @Test
    void supportsCadesAndAsicPackagingOnly() {
        for (KrAdesLevel level : KrAdesLevel.values()) {
            assertThat(adapter.supports(level, PackagingType.DETACHED)).isTrue();
            assertThat(adapter.supports(level, PackagingType.ENVELOPING)).isTrue();
            assertThat(adapter.supports(level, PackagingType.ASIC)).isTrue();
            assertThat(adapter.supports(level, PackagingType.ENVELOPED)).isFalse();
        }

        assertThat(adapter.supports(null, PackagingType.DETACHED)).isFalse();
    }

    @Test
    void mapsKrLevelsToDssCadesBaselineLevels() {
        assertThat(adapter.toDssSignatureLevel(KrAdesLevel.KR_B)).isEqualTo(SignatureLevel.CAdES_BASELINE_B);
        assertThat(adapter.toDssSignatureLevel(KrAdesLevel.KR_T)).isEqualTo(SignatureLevel.CAdES_BASELINE_T);
        assertThat(adapter.toDssSignatureLevel(KrAdesLevel.KR_LT)).isEqualTo(SignatureLevel.CAdES_BASELINE_LT);
        assertThat(adapter.toDssSignatureLevel(KrAdesLevel.KR_LTA)).isEqualTo(SignatureLevel.CAdES_BASELINE_LTA);
    }

    @Test
    void mapsGeneralCadesPackagingToDssPackaging() {
        assertThat(adapter.toDssSignaturePackaging(PackagingType.DETACHED)).isEqualTo(SignaturePackaging.DETACHED);
        assertThat(adapter.toDssSignaturePackaging(PackagingType.ENVELOPING)).isEqualTo(SignaturePackaging.ENVELOPING);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> adapter.toDssSignaturePackaging(PackagingType.ENVELOPED));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> adapter.toDssSignaturePackaging(PackagingType.ASIC));
    }

    @Test
    void createsEtsiCadesSignatureParameters() {
        CAdESSignatureParameters parameters =
                adapter.newSignatureParameters(KrAdesLevel.KR_LT, PackagingType.DETACHED);

        assertThat(parameters.isEn319122()).isTrue();
        assertThat(parameters.getSignatureLevel()).isEqualTo(SignatureLevel.CAdES_BASELINE_LT);
        assertThat(parameters.getSignaturePackaging()).isEqualTo(SignaturePackaging.DETACHED);
    }

    @Test
    void createsAsicWithCadesSignatureParameters() {
        var parameters = adapter.newAsicSignatureParameters(KrAdesLevel.KR_LTA);

        assertThat(parameters.isEn319122()).isTrue();
        assertThat(parameters.getSignatureLevel()).isEqualTo(SignatureLevel.CAdES_BASELINE_LTA);
        assertThat(parameters.getSignaturePackaging()).isEqualTo(SignaturePackaging.DETACHED);
        assertThat(parameters.aSiC().getContainerType()).isEqualTo(ASiCContainerType.ASiC_E);
    }
}
