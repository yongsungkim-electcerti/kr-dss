package com.electcerti.krdss.poc.rp.tlpoc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static com.electcerti.krdss.poc.rp.tlpoc.Fixtures.*;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;

class TlAcceptanceTest {
    @TempDir Path root;
    @Test void rejectsBadCandidatesKeepsSnapshotAndRestores() throws Exception {
        var anchor = identity("TL Root", null, true); var signer = identity("TL Signer", anchor, false);
        var ca = identity("Service", null, true);
        var store = new TlAcceptance(root, List.of(anchor.cert()), Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(store::capture).hasMessage("NO_VALID_TL");
        var first = tl(signer, 2, NOW.plusSeconds(3600), ca, ServiceStatus.ACCREDITED);
        store.adopt(first); var captured = store.capture();
        assertThat(store.adopt(first).sha256()).isEqualTo(captured.sha256());
        assertThatThrownBy(() -> store.adopt(tl(signer, 1, NOW.plusSeconds(3600), ca, ServiceStatus.ACCREDITED))).hasMessage("TL_ROLLBACK");
        assertThatThrownBy(() -> store.adopt(tl(signer, 2, NOW.plusSeconds(3600), ca, ServiceStatus.SUSPENDED))).hasMessage("TL_CONFLICT");
        assertThatThrownBy(() -> store.adopt(tl(signer, 3, NOW.minusSeconds(1), ca, ServiceStatus.ACCREDITED))).hasMessage("TL_EXPIRED");
        var stranger = identity("Stranger", null, true);
        assertThatThrownBy(() -> store.adopt(tl(stranger, 3, NOW.plusSeconds(3600), ca, ServiceStatus.ACCREDITED))).hasMessage("TL_CHAIN_UNTRUSTED");
        var tampered = new String(first, java.nio.charset.StandardCharsets.UTF_8).replace("시험 CA", "변조 CA").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(() -> store.adopt(tampered)).hasMessage("TL_SIGNATURE_INVALID");
        assertThat(store.capture().sha256()).isEqualTo(captured.sha256());
        store.adopt(tl(signer, 3, NOW.plusSeconds(3600), ca, ServiceStatus.SUSPENDED));
        assertThat(captured.sequence()).isEqualTo("2");
        assertThat(new TlAcceptance(root, List.of(anchor.cert()), Clock.fixed(NOW, ZoneOffset.UTC)).capture().sequence()).isEqualTo("3");
        assertThatThrownBy(() -> new TlAcceptance(root, List.of(anchor.cert()), Clock.fixed(NOW.plusSeconds(3600), ZoneOffset.UTC)).capture()).hasMessage("NO_VALID_TL");
    }
}
