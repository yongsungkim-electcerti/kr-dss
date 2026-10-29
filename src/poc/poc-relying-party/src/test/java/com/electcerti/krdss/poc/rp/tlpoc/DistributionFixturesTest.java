package com.electcerti.krdss.poc.rp.tlpoc;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.time.Instant;
import java.util.Base64;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;
import static com.electcerti.krdss.poc.rp.tlpoc.Fixtures.*;

/** 명시한 배포 준비 경로에만 새 시연 키·자료를 생성한다. 운영 키를 읽지 않는다. */
class DistributionFixturesTest {
    @Test void exportOnlyWhenExplicitlyRequested() throws Exception {
        String target = System.getenv("KRTL_DISTRIBUTION_FIXTURES");
        org.junit.jupiter.api.Assumptions.assumeTrue(target != null && !target.isBlank());
        Path dir = Path.of(target);
        if (Files.exists(dir)) throw new IllegalStateException("새 배포 자료 폴더만 허용합니다.");
        Files.createDirectories(dir.resolve("signer"));
        var anchor = identity("DEMO ONLY TL Root", null, true);
        var signer = identity("DEMO ONLY TL Signer", anchor, false);
        var ca = identity("DEMO ONLY Service CA", null, true);
        var leaf = identity("DEMO ONLY PDF Subscriber", ca, false);
        Instant expiry = Instant.now().plusSeconds(180L * 86400);
        Files.writeString(dir.resolve("tl-root.pem"), pem("CERTIFICATE", anchor.cert().getEncoded()));
        Files.writeString(dir.resolve("service-ca.pem"), pem("CERTIFICATE", ca.cert().getEncoded()));
        Files.writeString(dir.resolve("signer/key.pem"), pem("PRIVATE KEY", signer.keys().getPrivate().getEncoded()));
        Files.writeString(dir.resolve("signer/chain.pem"), pem("CERTIFICATE", signer.cert().getEncoded()) + pem("CERTIFICATE", anchor.cert().getEncoded()));
        Files.write(dir.resolve("signed.pdf"), PocPdfVerifierTest.signedPdf(leaf, ca));
        Files.write(dir.resolve("tl-1.xml"), tl(signer, 1, expiry, ca, ServiceStatus.ACCREDITED));
        Files.write(dir.resolve("tl-2.xml"), tl(signer, 2, expiry, ca, ServiceStatus.SUSPENDED));
        Files.writeString(dir.resolve("sample-info.json"), "{\"evaluationTime\":\"" + NOW + "\",\"nextUpdate\":\"" + expiry + "\",\"demoOnly\":true}");
    }
    private static String pem(String label, byte[] bytes) {
        return "-----BEGIN " + label + "-----\n" + Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(bytes) + "\n-----END " + label + "-----\n";
    }
}
