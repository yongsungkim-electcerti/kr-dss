package com.electcerti.krdss.tl.builder;

import com.electcerti.krdss.tl.model.*;
import com.electcerti.krdss.tl.model.KrTrustList.*;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrustServiceChangesTest {
    private static final Instant START = Instant.parse("2026-10-01T00:00:00Z");
    private static KeyPair firstKey, secondKey;
    private static X509Certificate first, renewed, otherKey, otherSubject;

    @BeforeAll static void certificates() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        firstKey = generator.generateKeyPair();
        secondKey = generator.generateKeyPair();
        first = certificate(firstKey, "CN=CA", 1);
        renewed = certificate(firstKey, "CN=CA", 2);
        otherKey = certificate(secondKey, "CN=CA", 3);
        otherSubject = certificate(firstKey, "CN=Other CA", 4);
    }

    private static X509Certificate certificate(KeyPair keys, String subject, int serial) throws Exception {
        var name = new X500Name(subject);
        var builder = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(serial),
                Date.from(START.minusSeconds(86400)), Date.from(START.plusSeconds(864000)),
                name, keys.getPublic());
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                new JcaX509ExtensionUtils().createSubjectKeyIdentifier(keys.getPublic()));
        return new JcaX509CertificateConverter().getCertificate(builder.build(
                new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate())));
    }

    private static TrustService service() throws Exception {
        var ski = new JcaX509ExtensionUtils().createSubjectKeyIdentifier(firstKey.getPublic()).getKeyIdentifier();
        var info = new ServiceInformation(PocTrustListProfile.serviceTypeUri(PocTrustListProfile.ServiceType.CA),
                List.of(new LocalizedName("ko", "CA")),
                new CurrentDigitalIdentity(List.of(new BinaryValue(first.getEncoded())),
                        List.of(new BinaryValue(ski)), first.getSubjectX500Principal().getName()),
                PocTrustListProfile.STATUS_ACCREDITED, START,
                PocTrustListProfile.extensions(PocTrustListProfile.ServiceType.CA,
                        PocTrustListProfile.TrustDomain.JOINT, PocTrustListProfile.TrustPointLevel.CA));
        return new TrustService("stable-id", info, List.of());
    }

    @Test void sameKeyRenewalDeduplicatesWithoutChangingHistoryOrStart() throws Exception {
        var original = service();
        var value = new BinaryValue(renewed.getEncoded());
        var updated = TrustServiceChanges.renewCertificates(original, List.of(value, value));
        assertEquals("stable-id", updated.serviceId());
        assertEquals(2, updated.current().digitalIdentity().certificates().size());
        assertEquals(START, updated.current().statusStartingTime());
        assertEquals(original.history(), updated.history());
        assertEquals(1, original.current().digitalIdentity().certificates().size());
    }

    @Test void differentKeyOrSubjectRequiresNewService() throws Exception {
        var original = service();
        assertThrows(IllegalArgumentException.class, () -> TrustServiceChanges.renewCertificates(original,
                List.of(new BinaryValue(otherKey.getEncoded()))));
        assertThrows(IllegalArgumentException.class, () -> TrustServiceChanges.renewCertificates(original,
                List.of(new BinaryValue(otherSubject.getEncoded()))));
    }

    @Test void changeKeepsOldNamesExtensionsAndSkiAndUsesFirstIssuanceTime() throws Exception {
        var original = service();
        var current = original.current();
        Instant changedAt = START.plusSeconds(10);
        var next = new ServiceInformation(current.serviceTypeUri(), List.of(new LocalizedName("ko", "새 이름")),
                current.digitalIdentity(), PocTrustListProfile.STATUS_SUSPENDED, changedAt, List.of());
        var changed = TrustServiceChanges.changeInformation(original, next, changedAt);
        assertEquals(1, changed.history().size());
        assertEquals(current.names(), changed.history().get(0).names());
        assertEquals(current.extensions(), changed.history().get(0).extensions());
        assertEquals(current.digitalIdentity().subjectKeyIdentifiers(),
                changed.history().get(0).digitalIdentity().subjectKeyIdentifiers());
        assertEquals(PocTrustListProfile.STATUS_SUSPENDED, TrustServiceTimeline.resolve(changed, changedAt).statusUri());
        // Unchanged data during later reissuance must not create a second history interval.
        assertSame(changed, TrustServiceChanges.changeInformation(changed, next, changedAt.plusSeconds(20)));
        assertThrows(IllegalArgumentException.class,
                () -> TrustServiceChanges.changeInformation(original, next, changedAt.plusSeconds(1)));
    }

    @Test void renewedModelStillSignsAndReadsThroughLegacyJwsPipeline() throws Exception {
        var list = new KrTrustList(new SchemeInformation(6, BigInteger.valueOf(7), "PoC",
                START, START.plusSeconds(100), 65535), List.of(new TrustServiceProvider("provider", List.of(service()))));
        byte[] signed = new KrTrustListBuilder().sign(list, firstKey.getPrivate(), List.of(first));
        var parsed = SignedKrTrustList.parse(signed);
        assertTrue(parsed.signatureValid());
        assertEquals(list, parsed.trustList());
    }

    @Test void changingStatusDoesNotDropEarlierCertificates() throws Exception {
        var original = service();
        var current = original.current();
        var next = new ServiceInformation(current.serviceTypeUri(), current.names(),
                new CurrentDigitalIdentity(List.of(new BinaryValue(renewed.getEncoded())),
                        current.digitalIdentity().subjectKeyIdentifiers(), current.digitalIdentity().subjectName()),
                PocTrustListProfile.STATUS_SUSPENDED, START.plusSeconds(10), current.extensions());
        var changed = TrustServiceChanges.changeInformation(original, next, START.plusSeconds(10));
        assertEquals(2, changed.current().digitalIdentity().certificates().size());
        assertTrue(changed.current().digitalIdentity().certificates().contains(new BinaryValue(first.getEncoded())));
    }
}
