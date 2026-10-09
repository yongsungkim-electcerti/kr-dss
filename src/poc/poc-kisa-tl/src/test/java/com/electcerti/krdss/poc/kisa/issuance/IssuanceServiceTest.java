package com.electcerti.krdss.poc.kisa.issuance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.electcerti.krdss.poc.kisa.issuance.Issuance.Purpose;
import com.electcerti.krdss.poc.kisa.issuance.Issuance.Request;
import com.electcerti.krdss.poc.kisa.registry.RegistryException;
import com.electcerti.krdss.poc.kisa.registry.RegistryException.Code;
import com.electcerti.krdss.poc.kisa.registry.RegistryService;
import com.electcerti.krdss.poc.kisa.registry.RegistryService.ProviderInput;
import com.electcerti.krdss.poc.kisa.registry.RegistryService.ServiceInput;
import com.electcerti.krdss.poc.kisa.registry.RegistryService.StatusChange;
import com.electcerti.krdss.poc.kisa.registry.RegistryStore;
import com.electcerti.krdss.tl.builder.KrTrustListXml;
import com.electcerti.krdss.tl.builder.KrTrustListXmlSigner;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;
import com.electcerti.krdss.tl.model.PocTrustListProfile;
import com.electcerti.krdss.tl.model.PocTrustListProfile.ServiceType;
import com.electcerti.krdss.tl.model.PocTrustListProfile.TrustDomain;
import com.electcerti.krdss.tl.model.PocTrustListProfile.TrustPointLevel;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IssuanceServiceTest {
    private static final Instant T0 = Instant.parse("2026-10-09T00:00:00Z");

    /** 시험 중 시각을 앞으로 옮길 수 있는 시계. */
    static final class MovableClock extends Clock {
        Instant now = T0;
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration d) { now = now.plus(d); }
    }

    @TempDir Path root;
    @TempDir Path signerDir;
    private final MovableClock clock = new MovableClock();
    private RegistryStore store;
    private RegistryService registry;
    private IssuanceService issuance;
    private X509Certificate signerCert;
    private String providerId;
    private String serviceId;
    private KeyPair caKeys;

    @BeforeEach
    void setUp() throws Exception {
        writeSigner();
        open();
        providerId = registry.addProvider(1, new ProviderInput("가상공동인증A", null, null, null))
                .draft().providers().get(0).providerId();
        caKeys = keyPair();
        var ca = certificate(caKeys, "CN=가상공동인증A CA, C=KR", caKeys.getPrivate(), null, true);
        serviceId = registry.addService(revision(), providerId, new ServiceInput(ServiceType.CA, "가상공동인증A CA",
                null, TrustDomain.JOINT, TrustPointLevel.CA, T0.minus(Duration.ofDays(1)),
                List.of(Base64.getEncoder().encodeToString(ca.getEncoded()))))
                .draft().providers().get(0).services().get(0).serviceId();
    }

    private void open() {
        store = RegistryStore.open(root, clock);
        registry = new RegistryService(store, clock);
        issuance = new IssuanceService(store, signerDir, clock);
    }

    @AfterEach
    void close() {
        store.close();
    }

    private long revision() {
        return store.current().revision();
    }

    private Request request(Purpose purpose, String sequence) {
        return new Request(UUID.randomUUID().toString(), revision(), purpose, sequence, null, null);
    }

    private static void assertCode(Throwable e, Code code) {
        assertThat(e).isInstanceOf(RegistryException.class);
        assertThat(((RegistryException) e).code()).isEqualTo(code);
    }

    @Test
    void firstBaselineIsSignedStoredAndAdvancesNormalState() throws Exception {
        var result = issuance.issue(request(Purpose.BASELINE, null));
        var manifest = result.manifest();
        assertThat(result.replay()).isFalse();
        assertThat(manifest.purpose()).isEqualTo(Purpose.BASELINE);
        assertThat(manifest.sequenceNumber()).isEqualTo("1");
        assertThat(manifest.diagnostics()).isEmpty();
        assertThat(store.state().baselineIssuanceId()).isEqualTo(manifest.issuanceId());
        assertThat(store.state().normalSequence()).isEqualTo("1");
        assertThat(store.current().baselineIssuanceId()).isEqualTo(manifest.issuanceId());

        byte[] xml = issuance.signedXml(manifest.issuanceId());
        var check = KrTrustListXmlSigner.check(xml);
        assertThat(check.valid()).isTrue();
        assertThat(check.signer()).isEqualTo(signerCert);
        assertThat(KrTrustListXml.fromXml(xml).schemeInformation().sequenceNumber()).isEqualTo(BigInteger.ONE);
        Path dir = root.resolve("issued").resolve(manifest.issuanceId());
        for (var file : List.of("signed.xml", "signed.sha256", "snapshot.json", "profile.json", "manifest.json")) {
            assertThat(dir.resolve(file)).exists();
        }
        assertThat(Files.readString(dir.resolve("manifest.json"))).doesNotContain("PRIVATE KEY");
    }

    @Test
    void sameRequestReturnsStoredResultWithoutResigning() {
        var request = request(Purpose.BASELINE, null);
        var first = issuance.issue(request);
        var again = issuance.issue(request);
        assertThat(again.replay()).isTrue();
        assertThat(again.manifest()).isEqualTo(first.manifest());
        assertThat(issuance.list()).hasSize(1);
        var changed = new Request(request.requestId(), request.expectedDraftRevision(), Purpose.TRIAL, "9", null, null);
        assertThatThrownBy(() -> issuance.issue(changed)).satisfies(e -> assertCode(e, Code.REQUEST_CONFLICT));
    }

    @Test
    void semanticErrorBecomesTrialAndKeepsNormalState() {
        var baseline = issuance.issue(request(Purpose.BASELINE, null)).manifest();
        clock.advance(Duration.ofHours(1));
        var trial = issuance.issue(request(Purpose.BASELINE, "100")).manifest();
        assertThat(trial.requestedPurpose()).isEqualTo(Purpose.BASELINE);
        assertThat(trial.purpose()).isEqualTo(Purpose.TRIAL);
        assertThat(trial.diagnostics()).extracting(Issuance.Diagnostic::code).contains("SEQUENCE_GAP");
        assertThat(store.state().baselineIssuanceId()).isEqualTo(baseline.issuanceId());
        assertThat(store.state().normalSequence()).isEqualTo("1");
        assertThat(issuance.plan(null, null, null, null).recommendedSequence()).isEqualTo("2");

        // 낮은 순번·과거 시각도 시험 발행으로 생성된다.
        var low = issuance.issue(new Request(UUID.randomUUID().toString(), revision(), Purpose.TRIAL, "1",
                T0.minus(Duration.ofDays(3)), null)).manifest();
        assertThat(low.diagnostics()).extracting(Issuance.Diagnostic::code)
                .contains("SEQUENCE_NOT_INCREASING", "ISSUE_TIME_NOT_INCREASING");
        assertThat(store.state().committed()).hasSize(3);
    }

    @Test
    void onlyFinalChangeAgainstBaselineBecomesHistory() throws Exception {
        issuance.issue(request(Purpose.BASELINE, null));
        clock.advance(Duration.ofHours(1));
        registry.changeStatus(revision(), providerId, serviceId,
                new StatusChange(ServiceStatus.SUSPENDED, clock.instant().plus(Duration.ofMinutes(10)), "정지"));
        registry.changeStatus(revision(), providerId, serviceId,
                new StatusChange(ServiceStatus.ACCREDITED, clock.instant().plus(Duration.ofMinutes(20)), "복원"));
        clock.advance(Duration.ofMinutes(30));
        var second = issuance.issue(request(Purpose.BASELINE, null)).manifest();
        assertThat(second.purpose()).isEqualTo(Purpose.BASELINE);
        var service = KrTrustListXml.fromXml(issuance.signedXml(second.issuanceId()))
                .trustServiceProviders().get(0).services().get(0);
        // 두 번 바꿨어도 직전 정상 발행 대비 한 구간만 이력이 된다.
        assertThat(service.history()).hasSize(1);
        assertThat(service.current().statusUri()).isEqualTo(PocTrustListProfile.STATUS_ACCREDITED);

        // 같은 키 인증서 갱신 뒤 재발행은 이력을 추가하지 않는다.
        var renewed = certificate(caKeys, "CN=가상공동인증A CA, C=KR", caKeys.getPrivate(), null, true);
        registry.addCertificates(revision(), providerId, serviceId,
                List.of(Base64.getEncoder().encodeToString(renewed.getEncoded())));
        clock.advance(Duration.ofMinutes(5));
        var third = issuance.issue(request(Purpose.BASELINE, null)).manifest();
        var again = KrTrustListXml.fromXml(issuance.signedXml(third.issuanceId()))
                .trustServiceProviders().get(0).services().get(0);
        assertThat(third.sequenceNumber()).isEqualTo("3");
        assertThat(again.history()).hasSize(1);
        assertThat(again.current().digitalIdentity().certificates()).hasSize(2);
    }

    @Test
    void staleDraftRevisionIsRejected() {
        var request = new Request(UUID.randomUUID().toString(), revision() - 1, Purpose.BASELINE, null, null, null);
        assertThatThrownBy(() -> issuance.issue(request)).satisfies(e -> assertCode(e, Code.REVISION_CONFLICT));
        assertThat(store.state().committed()).isEmpty();
    }

    @Test
    void interruptionBeforeCommitLeavesOrphanAndNoSuccess() {
        issuance.beforeCommit = () -> {
            throw new IllegalStateException("주입된 중단");
        };
        var request = request(Purpose.BASELINE, null);
        assertThatThrownBy(() -> issuance.issue(request)).satisfies(e -> assertCode(e, Code.STORAGE_FAILED));
        assertThat(store.state().committed()).isEmpty();
        assertThat(store.state().baselineIssuanceId()).isNull();
        assertThat(issuance.list()).singleElement().satisfies(e -> assertThat(e.state()).isEqualTo("ORPHAN"));

        store.close();
        open();
        assertThat(store.state().committed()).isEmpty();
        assertThatThrownBy(() -> issuance.issue(request)).isInstanceOf(RegistryException.class);
        // 새 requestId로는 정상 발행된다.
        assertThat(issuance.issue(request(Purpose.BASELINE, null)).manifest().purpose()).isEqualTo(Purpose.BASELINE);
    }

    @Test
    void missingSignerKeyFailsWithoutCommit() throws Exception {
        Files.delete(signerDir.resolve("key.pem"));
        assertThatThrownBy(() -> issuance.issue(request(Purpose.BASELINE, null)))
                .satisfies(e -> assertCode(e, Code.KEY_READ_FAILED));
        assertThat(store.state().committed()).isEmpty();
        assertThat(issuance.plan(null, null, null, null).signerReady()).isFalse();
    }

    @Test
    void committedIssuanceSurvivesRestartAndDetectsTampering() throws Exception {
        var manifest = issuance.issue(request(Purpose.BASELINE, null)).manifest();
        store.close();
        open();
        assertThat(store.state().baselineIssuanceId()).isEqualTo(manifest.issuanceId());
        assertThat(IssuanceService.sha256(issuance.signedXml(manifest.issuanceId()))).isEqualTo(manifest.xmlSha256());

        Path xml = root.resolve("issued").resolve(manifest.issuanceId()).resolve("signed.xml");
        Files.writeString(xml, Files.readString(xml, StandardCharsets.UTF_8) + " ", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> issuance.signedXml(manifest.issuanceId()))
                .satisfies(e -> assertCode(e, Code.STATE_UNAVAILABLE));
        assertThat(issuance.list().get(0).intact()).isFalse();
    }

    // ---------------------------------------------------------------- 시험 키

    private void writeSigner() throws Exception {
        var rootKeys = keyPair();
        var caKeys = keyPair();
        var signerKeys = keyPair();
        var rootCert = certificate(rootKeys, "CN=Test TL RootCA, C=KR", rootKeys.getPrivate(), null, true);
        var caCert = certificate(caKeys, "CN=Test TL CA, C=KR", rootKeys.getPrivate(), rootCert, true);
        signerCert = certificate(signerKeys, "CN=Test TL Signer, C=KR", caKeys.getPrivate(), caCert, false);
        Files.writeString(signerDir.resolve("key.pem"), "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(signerKeys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        var chain = new StringBuilder();
        for (var cert : List.of(signerCert, caCert, rootCert)) {
            chain.append("-----BEGIN CERTIFICATE-----\n")
                    .append(Base64.getMimeEncoder().encodeToString(cert.getEncoded()))
                    .append("\n-----END CERTIFICATE-----\n");
        }
        Files.writeString(signerDir.resolve("chain.pem"), chain);
    }

    private static KeyPair keyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return generator.generateKeyPair();
    }

    private static long serial = 1;

    private static X509Certificate certificate(KeyPair keys, String subject, PrivateKey issuerKey,
            X509Certificate issuer, boolean ca) throws Exception {
        var name = new X500Principal(subject);
        var builder = new JcaX509v3CertificateBuilder(issuer == null ? name : issuer.getSubjectX500Principal(),
                BigInteger.valueOf(serial++), Date.from(T0.minus(Duration.ofDays(2))),
                Date.from(T0.plus(Duration.ofDays(365))), name, keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                new JcaX509ExtensionUtils().createSubjectKeyIdentifier(keys.getPublic()));
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey)));
    }
}
