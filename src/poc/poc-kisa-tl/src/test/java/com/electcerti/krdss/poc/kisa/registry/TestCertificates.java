package com.electcerti.krdss.poc.kisa.registry;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** 시험용 자체 서명 CA 인증서. */
final class TestCertificates {
    static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");
    private static final AtomicLong SERIAL = new AtomicLong(1);

    private TestCertificates() {
    }

    static KeyPair keyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return generator.generateKeyPair();
    }

    static X509Certificate ca(KeyPair keys, String subject, boolean withSki) throws Exception {
        var name = new X500Principal(subject);
        var builder = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(SERIAL.getAndIncrement()),
                Date.from(NOW.minusSeconds(86_400)), Date.from(NOW.plusSeconds(365L * 86_400)), name,
                keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        if (withSki) {
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                    new JcaX509ExtensionUtils().createSubjectKeyIdentifier(keys.getPublic()));
        }
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(keys.getPrivate())));
    }

    static String der(X509Certificate certificate) throws Exception {
        return Base64.getEncoder().encodeToString(certificate.getEncoded());
    }

    static String pem(X509Certificate certificate) throws Exception {
        var body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(certificate.getEncoded());
        var text = "-----BEGIN CERTIFICATE-----\n" + body + "\n-----END CERTIFICATE-----\n";
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.US_ASCII));
    }
}
