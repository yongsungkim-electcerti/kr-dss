package com.electcerti.krdss.dss.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.electcerti.krdss.ades.core.KrAdesFormat;
import com.electcerti.krdss.ades.core.KrAdesLevel;
import com.electcerti.krdss.ades.core.PackagingType;
import com.electcerti.krdss.dss.api.Dtos.SignRequest;
import com.electcerti.krdss.dss.api.Dtos.SignResult;
import com.electcerti.krdss.dss.api.Dtos.VerifyRequest;
import com.electcerti.krdss.dss.api.Dtos.VerifyResult;
import com.electcerti.krdss.dss.api.KrDssService;
import com.electcerti.krdss.dss.api.VerificationStatus;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class KrDssServiceImplCadesTest {

    @Test
    void signsSimpleMessageWithCadesAndVerifiesSignature() {
        KrDssService service = new KrDssServiceImpl();
        byte[] document = "KR-DSS CAdES test message".getBytes(StandardCharsets.UTF_8);

        SignResult signResult = service.sign(new SignRequest(
                document,
                KrAdesFormat.CADES,
                KrAdesLevel.KR_B,
                PackagingType.ENVELOPING));

        assertThat(signResult.format()).isEqualTo(KrAdesFormat.CADES);
        assertThat(signResult.level()).isEqualTo(KrAdesLevel.KR_B);
        assertThat(signResult.signedDocument()).isNotEmpty();

        VerifyResult verifyResult = service.verify(new VerifyRequest(signResult.signedDocument(), KrAdesFormat.CADES));

        assertThat(verifyResult.status()).isEqualTo(VerificationStatus.TOTAL_PASSED);
        assertThat(new String(verifyResult.report(), StandardCharsets.UTF_8))
                .contains("CAdES signature verification passed");
    }

    @Test
    void rejectsTamperedCadesSignature() {
        KrDssService service = new KrDssServiceImpl();
        byte[] document = "KR-DSS CAdES tamper test message".getBytes(StandardCharsets.UTF_8);

        SignResult signResult = service.sign(new SignRequest(
                document,
                KrAdesFormat.CADES,
                KrAdesLevel.KR_B,
                PackagingType.ENVELOPING));
        byte[] tampered = signResult.signedDocument().clone();
        tampered[tampered.length - 1] ^= 0x01;

        VerifyResult verifyResult = service.verify(new VerifyRequest(tampered, KrAdesFormat.CADES));

        assertThat(verifyResult.status()).isEqualTo(VerificationStatus.TOTAL_FAILED);
    }
}
