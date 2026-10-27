package com.electcerti.krdss.poc.rp.tlpoc;

import java.security.*;
import java.security.cert.X509Certificate;
import java.time.*;
import java.util.*;
import java.math.BigInteger;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.*;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import com.electcerti.krdss.tl.model.*;
import com.electcerti.krdss.tl.builder.*;

final class Fixtures {
    static final Instant NOW = Instant.parse("2026-10-27T00:00:00Z");
    record Identity(KeyPair keys, X509Certificate cert) { }
    static Identity identity(String name, Identity issuer, boolean ca) throws Exception {
        var gen = KeyPairGenerator.getInstance("RSA"); gen.initialize(2048); var keys = gen.generateKeyPair();
        var subject = new X500Principal("CN=" + name);
        var builder = new JcaX509v3CertificateBuilder(issuer == null ? subject : issuer.cert().getSubjectX500Principal(),
                new BigInteger(80, new SecureRandom()), Date.from(NOW.minusSeconds(86400)), Date.from(NOW.plusSeconds(86400 * 365)), subject, keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        var cert = new JcaX509CertificateConverter().getCertificate(builder.build(new JcaContentSignerBuilder("SHA256withRSA")
                .build(issuer == null ? keys.getPrivate() : issuer.keys().getPrivate())));
        return new Identity(keys, cert);
    }
    static byte[] tl(Identity signer, int seq, Instant expires, Identity service, KrTrustList.ServiceStatus status) throws Exception {
        var list = new KrTrustList(new KrTrustList.SchemeInformation(6, BigInteger.valueOf(seq), "시험 운영자",
                NOW.minusSeconds(100 - seq), expires, 65535), List.of(new KrTrustList.TrustServiceProvider("시험 사업자",
                List.of(new KrTrustList.TrustService("test-ca", new KrTrustList.ServiceInformation(
                        PocTrustListProfile.serviceTypeUri(PocTrustListProfile.ServiceType.CA),
                        List.of(new KrTrustList.LocalizedName("ko", "시험 CA")),
                        new KrTrustList.CurrentDigitalIdentity(List.of(new KrTrustList.BinaryValue(service.cert().getEncoded())), List.of(), null),
                        PocTrustListProfile.statusUri(status), NOW.minusSeconds(1000), List.of()), List.of())))));
        return KrTrustListXmlSigner.sign(KrTrustListXml.toXml(list), signer.keys().getPrivate(), List.of(signer.cert()), NOW.minusSeconds(100));
    }
}
