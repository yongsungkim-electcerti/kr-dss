package com.electcerti.krdss.poc.kisa.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.electcerti.krdss.poc.kisa.registry.RegistryException.Code;
import com.electcerti.krdss.poc.kisa.registry.RegistryService.ProviderInput;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegistryStoreTest {
    private static final Clock CLOCK = Clock.fixed(TestCertificates.NOW, ZoneOffset.UTC);

    @TempDir Path root;

    private static RegistryService service(RegistryStore store) {
        return new RegistryService(store, CLOCK);
    }

    private static ProviderInput provider(String name) {
        return new ProviderInput(name, null, null, null);
    }

    private static void assertCode(Throwable e, Code code) {
        assertThat(e).isInstanceOf(RegistryException.class);
        assertThat(((RegistryException) e).code()).isEqualTo(code);
    }

    @Test
    void firstStartCreatesRevisionOneAndRestoresIt() {
        try (var store = RegistryStore.open(root, CLOCK)) {
            assertThat(store.current().revision()).isEqualTo(1);
            assertThat(store.health().available()).isTrue();
        }
        assertThat(root.resolve("registry/revisions/r-00000001.json")).exists();
        assertThat(root.resolve("publication/state.json")).exists();
        try (var store = RegistryStore.open(root, CLOCK)) {
            assertThat(store.current().revision()).isEqualTo(1);
        }
    }

    @Test
    void savedEditsAreRestoredAfterRestart() {
        RegistryDraft saved;
        try (var store = RegistryStore.open(root, CLOCK)) {
            saved = service(store).addProvider(1,
                    new ProviderInput("가상인증A", "Virtual A", null, "https://a.example")).draft();
            assertThat(saved.revision()).isEqualTo(2);
            assertThat(saved.parentRevision()).isEqualTo(1);
        }
        try (var store = RegistryStore.open(root, CLOCK)) {
            assertThat(store.current()).isEqualTo(saved);
        }
    }

    @Test
    void staleRevisionIsRejectedWithoutOverwriting() {
        try (var store = RegistryStore.open(root, CLOCK)) {
            var service = service(store);
            service.addProvider(1, provider("가상인증A"));
            assertThatThrownBy(() -> service.addProvider(1, provider("가상인증B")))
                    .satisfies(e -> assertCode(e, Code.REVISION_CONFLICT));
            assertThat(store.current().providers()).extracting(RegistryDraft.ProviderRecord::name)
                    .containsExactly("가상인증A");
        }
    }

    @Test
    void failureBeforeStateSwapKeepsPreviousDraft() {
        try (var store = RegistryStore.open(root, CLOCK)) {
            store.beforeStateSwap = () -> {
                throw new IllegalStateException("주입된 중단");
            };
            assertThatThrownBy(() -> service(store).addProvider(1, provider("가상인증A")))
                    .satisfies(e -> assertCode(e, Code.STORAGE_FAILED));
            assertThat(store.current().revision()).isEqualTo(1);
        }
        // 교체 전에 남은 revision 2는 미확정 잔여물이다. 재기동 후에도 활성 초안은 1이다.
        assertThat(root.resolve("registry/revisions/r-00000002.json")).exists();
        try (var store = RegistryStore.open(root, CLOCK)) {
            assertThat(store.current().revision()).isEqualTo(1);
            assertThat(store.current().providers()).isEmpty();
            var next = service(store).addProvider(1, provider("가상인증A")).draft();
            assertThat(next.revision()).isEqualTo(3);
            assertThat(store.revisions(10)).anySatisfy(r -> {
                assertThat(r.revision()).isEqualTo(2);
                assertThat(r.onActiveChain()).isFalse();
            });
        }
    }

    @Test
    void secondWriterOnSameRootFailsToStart() {
        try (var store = RegistryStore.open(root, CLOCK)) {
            assertThatThrownBy(() -> RegistryStore.open(root, CLOCK)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void tamperedActiveRevisionStopsWrites() throws Exception {
        try (var store = RegistryStore.open(root, CLOCK)) {
            service(store).addProvider(1, provider("가상인증A"));
        }
        Path file = root.resolve("registry/revisions/r-00000002.json");
        Files.writeString(file, Files.readString(file).replace("가상인증A", "변조됨"));
        try (var store = RegistryStore.open(root, CLOCK)) {
            assertThat(store.health().available()).isFalse();
            assertThatThrownBy(store::current).satisfies(e -> assertCode(e, Code.STATE_UNAVAILABLE));
            assertThatThrownBy(() -> service(store).addProvider(2, provider("B")))
                    .satisfies(e -> assertCode(e, Code.STATE_UNAVAILABLE));
        }
    }

    @Test
    void revisionsWithoutStateAreNotAutoSelected() throws Exception {
        try (var store = RegistryStore.open(root, CLOCK)) {
            service(store).addProvider(1, provider("가상인증A"));
        }
        Files.delete(root.resolve("publication/state.json"));
        try (var store = RegistryStore.open(root, CLOCK)) {
            assertThat(store.health().available()).isFalse();
        }
    }

    @Test
    void unchangedEditDoesNotCreateRevision() {
        try (var store = RegistryStore.open(root, CLOCK)) {
            var service = service(store);
            var draft = service.addProvider(1, provider("가상인증A")).draft();
            var id = draft.providers().get(0).providerId();
            assertThat(service.updateProvider(2, id, provider("가상인증A")).draft().revision()).isEqualTo(2);
        }
    }
}
