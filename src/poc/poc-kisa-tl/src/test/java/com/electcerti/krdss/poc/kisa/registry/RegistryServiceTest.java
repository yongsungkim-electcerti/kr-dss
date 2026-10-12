package com.electcerti.krdss.poc.kisa.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.electcerti.krdss.poc.kisa.registry.RegistryException.Code;
import com.electcerti.krdss.poc.kisa.registry.RegistryService.ProviderInput;
import com.electcerti.krdss.poc.kisa.registry.RegistryService.ServiceInfoInput;
import com.electcerti.krdss.poc.kisa.registry.RegistryService.ServiceInput;
import com.electcerti.krdss.poc.kisa.registry.RegistryService.StatusChange;
import com.electcerti.krdss.tl.builder.KrTrustListXml;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;
import com.electcerti.krdss.tl.model.PocTrustListProfile;
import com.electcerti.krdss.tl.model.PocTrustListProfile.ServiceType;
import com.electcerti.krdss.tl.model.PocTrustListProfile.TrustDomain;
import com.electcerti.krdss.tl.model.PocTrustListProfile.TrustPointLevel;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegistryServiceTest {
    private static final Clock CLOCK = Clock.fixed(TestCertificates.NOW, ZoneOffset.UTC);
    private static final Instant START = Instant.parse("2026-10-01T00:00:00Z");

    private static KeyPair keyA, keyB;
    private static X509Certificate certA, certARenewed, certB, certNoSki;

    @TempDir Path root;
    private RegistryStore store;
    private RegistryService service;
    private String providerId;

    @BeforeAll
    static void certificates() throws Exception {
        keyA = TestCertificates.keyPair();
        keyB = TestCertificates.keyPair();
        certA = TestCertificates.ca(keyA, "CN=Virtual CA A,O=Virtual A,C=KR", true);
        certARenewed = TestCertificates.ca(keyA, "CN=Virtual CA A,O=Virtual A,C=KR", true);
        certB = TestCertificates.ca(keyB, "CN=Virtual CA A,O=Virtual A,C=KR", true);
        certNoSki = TestCertificates.ca(TestCertificates.keyPair(), "CN=No SKI CA,C=KR", false);
    }

    @BeforeEach
    void open() {
        store = RegistryStore.open(root, CLOCK);
        service = new RegistryService(store, CLOCK);
        providerId = service.addProvider(1, new ProviderInput("가상인증A", null, null, null))
                .draft().providers().get(0).providerId();
    }

    @AfterEach
    void close() {
        store.close();
    }

    private long revision() {
        return store.current().revision();
    }

    private RegistryDraft.ServiceRecord addCa(String certificate) {
        var draft = service.addService(revision(), providerId, new ServiceInput(ServiceType.CA, "가상 CA",
                "Virtual CA", TrustDomain.JOINT, TrustPointLevel.CA, START, List.of(certificate))).draft();
        var services = draft.providers().get(0).services();
        return services.get(services.size() - 1);
    }

    private static void assertCode(Throwable e, Code code) {
        assertThat(e).isInstanceOf(RegistryException.class);
        assertThat(((RegistryException) e).code()).isEqualTo(code);
    }

    @Test
    void registersServiceWithExtractedCertificateValues() throws Exception {
        var service = addCa(TestCertificates.der(certA));
        assertThat(service.status()).isEqualTo(ServiceStatus.ACCREDITED);
        var cert = service.certificates().get(0);
        assertThat(cert.subject()).isEqualTo("CN=Virtual CA A,O=Virtual A,C=KR");
        assertThat(cert.skiFromExtension()).isTrue();
        assertThat(cert.subjectKeyIdentifier()).hasSize(40);
    }

    @Test
    void acceptsPemAndComputesMissingSki() throws Exception {
        var result = service.addService(revision(), providerId, new ServiceInput(ServiceType.CA, "SKI 없는 CA", null,
                TrustDomain.SIMPLE, TrustPointLevel.ROOT, START, List.of(TestCertificates.pem(certNoSki))));
        var cert = result.draft().providers().get(0).services().get(0).certificates().get(0);
        assertThat(cert.skiFromExtension()).isFalse();
        assertThat(cert.subjectKeyIdentifier()).hasSize(40);
        assertThat(result.warnings()).anyMatch(w -> w.contains("SubjectKeyIdentifier"));
    }

    @Test
    void renewalWithSameKeyKeepsServiceIdAndStatusStart() throws Exception {
        var original = addCa(TestCertificates.der(certA));
        var renewed = service.addCertificates(revision(), providerId, original.serviceId(),
                List.of(TestCertificates.der(certARenewed))).draft().providers().get(0).services().get(0);
        assertThat(renewed.serviceId()).isEqualTo(original.serviceId());
        assertThat(renewed.statusStartingTime()).isEqualTo(START);
        assertThat(renewed.certificates()).hasSize(2);
    }

    @Test
    void newKeyCannotBeAddedToExistingService() throws Exception {
        var original = addCa(TestCertificates.der(certA));
        assertThatThrownBy(() -> service.addCertificates(revision(), providerId, original.serviceId(),
                List.of(TestCertificates.der(certB)))).satisfies(e -> assertCode(e, Code.IDENTITY_CONFLICT));
    }

    @Test
    void newKeyIsRegisteredAsSeparateServiceButKeyCannotBeReused() throws Exception {
        addCa(TestCertificates.der(certA));
        var second = addCa(TestCertificates.der(certB));
        assertThat(store.current().providers().get(0).services()).hasSize(2);
        assertThat(second.certificates().get(0).subject()).isEqualTo("CN=Virtual CA A,O=Virtual A,C=KR");
        assertThatThrownBy(() -> addCa(TestCertificates.der(certARenewed)))
                .satisfies(e -> assertCode(e, Code.IDENTITY_CONFLICT));
    }

    @Test
    void mixedKeysInOneRegistrationAreRejected() throws Exception {
        assertThatThrownBy(() -> service.addService(revision(), providerId, new ServiceInput(ServiceType.CA, "혼합",
                null, TrustDomain.JOINT, TrustPointLevel.CA, START,
                List.of(TestCertificates.der(certA), TestCertificates.der(certB)))))
                .satisfies(e -> assertCode(e, Code.IDENTITY_CONFLICT));
    }

    @Test
    void trustPointLevelOnlyForCa() throws Exception {
        var der = TestCertificates.der(certA);
        assertThatThrownBy(() -> service.addService(revision(), providerId, new ServiceInput(ServiceType.CA, "CA",
                null, TrustDomain.JOINT, null, START, List.of(der)))).satisfies(e -> assertCode(e, Code.INVALID_INPUT));
        assertThatThrownBy(() -> service.addService(revision(), providerId, new ServiceInput(ServiceType.OCSP,
                "OCSP", null, TrustDomain.JOINT, TrustPointLevel.CA, START, List.of(der))))
                .satisfies(e -> assertCode(e, Code.INVALID_INPUT));
    }

    @Test
    void statusTransitionsFollowNormalRules() throws Exception {
        var ca = addCa(TestCertificates.der(certA));
        var id = ca.serviceId();
        var t1 = START.plusSeconds(3600);
        var suspended = service.changeStatus(revision(), providerId, id,
                new StatusChange(ServiceStatus.SUSPENDED, t1, "키 유출 의심")).draft();
        assertThat(suspended.providers().get(0).services().get(0).status()).isEqualTo(ServiceStatus.SUSPENDED);
        assertThatThrownBy(() -> service.changeStatus(revision(), providerId, id,
                new StatusChange(ServiceStatus.ACCREDITED, t1, "소급")))
                .satisfies(e -> assertCode(e, Code.INVALID_INPUT));
        service.changeStatus(revision(), providerId, id,
                new StatusChange(ServiceStatus.WITHDRAWN, t1.plusSeconds(60), "철회"));
        assertThatThrownBy(() -> service.changeStatus(revision(), providerId, id,
                new StatusChange(ServiceStatus.ACCREDITED, t1.plusSeconds(120), "재인정")))
                .satisfies(e -> assertCode(e, Code.INVALID_INPUT));
    }

    @Test
    void infoEditKeepsCertificatesAndChecksLevel() throws Exception {
        var ca = addCa(TestCertificates.der(certA));
        var edited = service.updateServiceInfo(revision(), providerId, ca.serviceId(),
                new ServiceInfoInput("가상 RootCA", null, TrustDomain.SIMPLE, TrustPointLevel.ROOT, null))
                .draft().providers().get(0).services().get(0);
        assertThat(edited.level()).isEqualTo(TrustPointLevel.ROOT);
        assertThat(edited.certificates()).isEqualTo(ca.certificates());
        assertThatThrownBy(() -> service.updateServiceInfo(revision(), providerId, ca.serviceId(),
                new ServiceInfoInput("가상 RootCA", null, TrustDomain.SIMPLE, null, null)))
                .satisfies(e -> assertCode(e, Code.INVALID_INPUT));
    }

    @Test
    void previewXmlCarriesCertificatesSkiAndProfileExtensions() throws Exception {
        var ca = addCa(TestCertificates.der(certA));
        service.addCertificates(revision(), providerId, ca.serviceId(), List.of(TestCertificates.der(certARenewed)));
        var list = KrTrustListXml.fromXml(service.previewXml());
        var parsed = list.trustServiceProviders().get(0).services().get(0);
        assertThat(parsed.current().serviceTypeUri())
                .isEqualTo(PocTrustListProfile.serviceTypeUri(ServiceType.CA));
        assertThat(parsed.current().statusUri()).isEqualTo(PocTrustListProfile.STATUS_ACCREDITED);
        assertThat(parsed.current().digitalIdentity().certificates()).hasSize(2);
        assertThat(parsed.current().digitalIdentity().subjectKeyIdentifiers()).hasSize(1);
        assertThat(parsed.current().extensions()).hasSize(2);
        assertThat(list.trustServiceProviders().get(0).name()).isEqualTo("가상인증A");
    }

    @Test
    void overviewCountsOnlyStoredState() throws Exception {
        addCa(TestCertificates.der(certA));
        var overview = service.overview();
        assertThat(overview.providers()).isEqualTo(1);
        assertThat(overview.services()).isEqualTo(1);
        assertThat(overview.byStatus()).containsEntry(ServiceStatus.ACCREDITED, 1);
        assertThat(overview.baselineIssuanceId()).isNull();
    }
}
