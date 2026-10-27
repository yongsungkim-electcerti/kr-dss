package com.electcerti.krdss.poc.rp.tlpoc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.electcerti.krdss.poc.rp.tlpoc.Fixtures.*;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;

class PocRunStoreTest {
    @TempDir Path root;
    @Test void persistsActualRunsComparesConditionsAndDetectsChangedEvidence() throws Exception {
        var anchor=identity("TL Root",null,true);var signer=identity("TL Signer",anchor,false);
        var ca=identity("Service",null,true);var leaf=identity("Subscriber",ca,false);
        var acceptance=new TlAcceptance(root.resolve("tl"),List.of(anchor.cert()),Clock.fixed(NOW,ZoneOffset.UTC));
        var store=new PocRunStore(root.resolve("runs"));var pdf=PocPdfVerifierTest.signedPdf(leaf,ca);
        acceptance.adopt(tl(signer,1,NOW.plusSeconds(3600),ca,ServiceStatus.ACCREDITED));
        var a=store.execute(pdf,new byte[0],acceptance.capture(),NOW);
        acceptance.adopt(tl(signer,2,NOW.plusSeconds(3600),ca,ServiceStatus.SUSPENDED));
        var b=store.execute(pdf,new byte[0],acceptance.capture(),NOW);
        var comparison=store.compare(a.id(),b.id());
        assertThat(comparison.comparable()).isTrue();assertThat(comparison.tlChanged()).isTrue();
        assertThat(comparison.differences()).contains("TL 신뢰 판단","서비스 상태").doesNotContain("문서 무결성");
        assertThat(new PocRunStore(root.resolve("runs")).read(a.id())).isEqualTo(a);
        var different=store.execute(pdf,new byte[0],acceptance.capture(),NOW.plusSeconds(1));
        assertThat(store.compare(a.id(),different.id()).mismatches()).contains("평가시각");
        var failed=store.execute(new byte[]{1,2},new byte[0],acceptance.capture(),NOW);
        assertThat(failed.execution()).isEqualTo("ERROR");assertThat(store.compare(a.id(),failed.id()).comparable()).isFalse();
        assertThatThrownBy(()->store.read("../secret")).hasMessage("RUN_ID_INVALID");
        assertThatThrownBy(()->store.execute(pdf,"-----BEGIN PRIVATE KEY-----".getBytes(java.nio.charset.StandardCharsets.US_ASCII),acceptance.capture(),NOW)).hasMessage("SECRET_CONTENT");
        Files.write(root.resolve("runs").resolve(a.id()).resolve("input.pdf"),new byte[]{0});
        assertThatThrownBy(()->store.read(a.id())).hasMessage("RESULT_INTEGRITY_ERROR");
    }
}
