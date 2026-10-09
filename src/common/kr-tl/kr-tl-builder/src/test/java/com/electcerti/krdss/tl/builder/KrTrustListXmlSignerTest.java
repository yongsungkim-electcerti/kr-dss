package com.electcerti.krdss.tl.builder;

import static org.assertj.core.api.Assertions.assertThat;

import com.electcerti.krdss.tl.model.KrTrustList;
import com.electcerti.krdss.tl.model.KrTrustList.*;
import com.electcerti.krdss.tl.model.PocTrustListProfile;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class KrTrustListXmlSignerTest {
    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");
    private static KeyPair signerKeys;
    private static X509Certificate signer;

    @BeforeAll
    static void keys() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        signerKeys = generator.generateKeyPair();
        var name = new X500Principal("CN=Test TL Signer, C=KR");
        var builder = new JcaX509v3CertificateBuilder(name, BigInteger.ONE, Date.from(NOW.minusSeconds(3600)),
                Date.from(NOW.plusSeconds(86_400 * 30L)), name, signerKeys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                new JcaX509ExtensionUtils().createSubjectKeyIdentifier(signerKeys.getPublic()));
        signer = new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(signerKeys.getPrivate())));
    }

    private static byte[] unsigned() throws Exception {
        var info = new ServiceInformation(PocTrustListProfile.serviceTypeUri(PocTrustListProfile.ServiceType.CA),
                List.of(new LocalizedName("ko", "가상 CA")),
                new CurrentDigitalIdentity(List.of(new BinaryValue(signer.getEncoded())), List.of(), null),
                PocTrustListProfile.STATUS_ACCREDITED, NOW,
                PocTrustListProfile.extensions(PocTrustListProfile.ServiceType.CA,
                        PocTrustListProfile.TrustDomain.JOINT, PocTrustListProfile.TrustPointLevel.CA));
        var list = new KrTrustList(new SchemeInformation(6, BigInteger.TWO, "운영자", NOW, NOW.plusSeconds(86_400), 65535),
                List.of(new TrustServiceProvider("p", "가상사업자", List.of(new TrustService("s", info, List.of())))));
        return KrTrustListXml.toXml(list);
    }

    @Test
    void signedXmlVerifiesAndStillParses() throws Exception {
        byte[] signed = KrTrustListXmlSigner.sign(unsigned(), signerKeys.getPrivate(), List.of(signer), NOW);
        var check = KrTrustListXmlSigner.check(signed);
        assertThat(check.valid()).as(check.detail()).isTrue();
        assertThat(check.signer()).isEqualTo(signer);
        var parsed = KrTrustListXml.fromXml(signed);
        assertThat(parsed.schemeInformation().sequenceNumber()).isEqualTo(BigInteger.TWO);
        assertThat(parsed.trustServiceProviders().get(0).services()).hasSize(1);
    }

    @Test
    void signatureFollowsTs119612AnnexB() throws Exception {
        byte[] signed = KrTrustListXmlSigner.sign(unsigned(), signerKeys.getPrivate(), List.of(signer), NOW);
        String xml = new String(signed, StandardCharsets.UTF_8);
        assertThat(xml).contains("URI=\"#kr-tl\"")
                .contains("http://www.w3.org/2000/09/xmldsig#enveloped-signature")
                .doesNotContain("xmldsig-filter2");
        int reference = xml.indexOf("URI=\"#kr-tl\"");
        String tlReference = xml.substring(reference, xml.indexOf("</ds:Reference>", reference));
        assertThat(tlReference.split("<ds:Transform ", -1)).hasSize(3);
        assertThat(tlReference.indexOf("enveloped-signature")).isLessThan(tlReference.indexOf("xml-exc-c14n"));
        assertThat(xml).contains("<ds:CanonicalizationMethod Algorithm=\"http://www.w3.org/2001/10/xml-exc-c14n#\"");
    }

    @Test
    void tamperedXmlFailsCheck() throws Exception {
        byte[] signed = KrTrustListXmlSigner.sign(unsigned(), signerKeys.getPrivate(), List.of(signer), NOW);
        var text = new String(signed, StandardCharsets.UTF_8).replace("가상사업자", "변조사업자");
        assertThat(KrTrustListXmlSigner.check(text.getBytes(StandardCharsets.UTF_8)).valid()).isFalse();
    }

    @Test
    void unsignedXmlFailsCheck() throws Exception {
        assertThat(KrTrustListXmlSigner.check(unsigned()).valid()).isFalse();
    }
}
