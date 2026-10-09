package com.electcerti.krdss.poc.kisa.issuance;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** 시험용 TL 서명 체인(Root → CA → Signer)과 인증서 생성. */
public final class TestPki {
    public static final Instant T0 = Instant.parse("2026-10-09T00:00:00Z");
    private static final AtomicLong SERIAL = new AtomicLong(1);

    private TestPki() {
    }

    /** signerDir에 key.pem·chain.pem을 쓰고 서명 인증서를 돌려준다. */
    public static X509Certificate writeSigner(Path signerDir) throws Exception {
        var rootKeys = keyPair();
        var caKeys = keyPair();
        var signerKeys = keyPair();
        var rootCert = certificate(rootKeys, "CN=Test TL RootCA, C=KR", rootKeys.getPrivate(), null, true);
        var caCert = certificate(caKeys, "CN=Test TL CA, C=KR", rootKeys.getPrivate(), rootCert, true);
        var signer = certificate(signerKeys, "CN=Test TL Signer, C=KR", caKeys.getPrivate(), caCert, false);
        Files.writeString(signerDir.resolve("key.pem"), "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(signerKeys.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        var chain = new StringBuilder();
        for (var cert : List.of(signer, caCert, rootCert)) {
            chain.append("-----BEGIN CERTIFICATE-----\n")
                    .append(Base64.getMimeEncoder().encodeToString(cert.getEncoded()))
                    .append("\n-----END CERTIFICATE-----\n");
        }
        Files.writeString(signerDir.resolve("chain.pem"), chain);
        return signer;
    }

    public static KeyPair keyPair() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        return generator.generateKeyPair();
    }

    public static X509Certificate certificate(KeyPair keys, String subject, PrivateKey issuerKey,
            X509Certificate issuer, boolean ca) throws Exception {
        var name = new X500Principal(subject);
        var builder = new JcaX509v3CertificateBuilder(issuer == null ? name : issuer.getSubjectX500Principal(),
                BigInteger.valueOf(SERIAL.getAndIncrement()), Date.from(T0.minus(Duration.ofDays(2))),
                Date.from(T0.plus(Duration.ofDays(365))), name, keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                new JcaX509ExtensionUtils().createSubjectKeyIdentifier(keys.getPublic()));
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey)));
    }
}
