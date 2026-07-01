package com.electcerti.krdss.dss.trust.hsm;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Date;

/**
 * HSM Attestation 테스트 PKI — Root CA(자가서명) + 장치 Attestation 인증서.
 */
final class HsmTestSupport {

    record Pki(KeyPair rootKp, X509Certificate root, KeyPair deviceKp, X509Certificate device) {
    }

    private HsmTestSupport() {
    }

    static KeyPair ec() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        return kpg.generateKeyPair();
    }

    /** Root CA → 장치 Attestation 인증서 체인. */
    static Pki pki() throws Exception {
        KeyPair rootKp = ec();
        X509Certificate root = selfSignedCa(rootKp, "CN=HSM Attestation Root CA,O=Vendor,C=KR");
        KeyPair deviceKp = ec();
        X509Certificate device = issue(deviceKp.getPublic(), "CN=HSM Device 01,O=Vendor,C=KR",
                rootKp, root, false);
        return new Pki(rootKp, root, deviceKp, device);
    }

    static X509Certificate selfSignedCa(KeyPair kp, String dn) throws Exception {
        X500Name name = new X500Name(dn);
        Instant now = Instant.now();
        var builder = new JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(1),
                Date.from(now.minusSeconds(3600)), Date.from(now.plusSeconds(3600L * 24 * 3650)),
                name, kp.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(kp.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    static X509Certificate issue(PublicKey subjectKey, String dn, KeyPair issuerKp,
                                 X509Certificate issuerCert, boolean ca) throws Exception {
        X500Name issuer = new JcaX509CertificateHolder(issuerCert).getSubject();
        Instant now = Instant.now();
        var builder = new JcaX509v3CertificateBuilder(
                issuer, new BigInteger(64, new SecureRandom()),
                Date.from(now.minusSeconds(3600)), Date.from(now.plusSeconds(3600L * 24 * 825)),
                new X500Name(dn), subjectKey);
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(ca));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(
                ca ? KeyUsage.keyCertSign | KeyUsage.cRLSign : KeyUsage.digitalSignature));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(issuerKp.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }
}
