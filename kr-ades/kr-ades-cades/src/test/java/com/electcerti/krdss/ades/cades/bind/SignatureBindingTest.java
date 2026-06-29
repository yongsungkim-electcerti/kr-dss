package com.electcerti.krdss.ades.cades.bind;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 특허-A 서명 결속부 — SignedAttrs 구성과 Challenge 파생 검증.
 */
class SignatureBindingTest {

    private static X509Certificate cert;
    private static X509Certificate otherCert;

    @BeforeAll
    static void setup() throws Exception {
        cert = selfSigned("CN=KR-DSS Test Signer");
        otherCert = selfSigned("CN=KR-DSS Other Signer");
    }

    private static byte[] sha256(String s) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(s.getBytes("UTF-8"));
    }

    @Test
    void challenge_is_deterministic_for_same_inputs() throws Exception {
        byte[] docDigest = sha256("계약서 본문");
        Instant t = Instant.parse("2026-06-28T00:00:00Z");

        var a = SignedAttrsBuilder.build(docDigest, t, cert, HashSuite.SHA_256);
        var b = SignedAttrsBuilder.build(docDigest, t, cert, HashSuite.SHA_256);

        assertThat(a.der()).isEqualTo(b.der());
        assertThat(SignatureBindingService.deriveChallenge(a))
                .isEqualTo(SignatureBindingService.deriveChallenge(b));
    }

    @Test
    void challenge_changes_when_document_changes() throws Exception {
        Instant t = Instant.parse("2026-06-28T00:00:00Z");
        var a = SignedAttrsBuilder.build(sha256("문서 A"), t, cert, HashSuite.SHA_256);
        var b = SignedAttrsBuilder.build(sha256("문서 B"), t, cert, HashSuite.SHA_256);
        assertThat(SignatureBindingService.deriveChallenge(a))
                .isNotEqualTo(SignatureBindingService.deriveChallenge(b));
    }

    @Test
    void challenge_changes_when_signing_time_changes() throws Exception {
        byte[] docDigest = sha256("동일 문서");
        var a = SignedAttrsBuilder.build(docDigest, Instant.parse("2026-06-28T00:00:00Z"), cert, HashSuite.SHA_256);
        var b = SignedAttrsBuilder.build(docDigest, Instant.parse("2026-06-28T09:00:00Z"), cert, HashSuite.SHA_256);
        assertThat(SignatureBindingService.deriveChallenge(a))
                .isNotEqualTo(SignatureBindingService.deriveChallenge(b));
    }

    @Test
    void challenge_changes_when_signer_certificate_changes() throws Exception {
        byte[] docDigest = sha256("동일 문서");
        Instant t = Instant.parse("2026-06-28T00:00:00Z");
        var a = SignedAttrsBuilder.build(docDigest, t, cert, HashSuite.SHA_256);
        var b = SignedAttrsBuilder.build(docDigest, t, otherCert, HashSuite.SHA_256);
        assertThat(SignatureBindingService.deriveChallenge(a))
                .isNotEqualTo(SignatureBindingService.deriveChallenge(b));
    }

    @Test
    void crypto_agility_different_hash_yields_different_challenge() throws Exception {
        byte[] docDigest256 = MessageDigest.getInstance("SHA-256").digest("문서".getBytes("UTF-8"));
        byte[] docDigest384 = MessageDigest.getInstance("SHA-384").digest("문서".getBytes("UTF-8"));
        Instant t = Instant.parse("2026-06-28T00:00:00Z");
        var a = SignedAttrsBuilder.build(docDigest256, t, cert, HashSuite.SHA_256);
        var b = SignedAttrsBuilder.build(docDigest384, t, cert, HashSuite.SHA_384);
        assertThat(SignatureBindingService.deriveChallenge(a))
                .isNotEqualTo(SignatureBindingService.deriveChallenge(b));
        assertThat(HashSuite.SM3.supported()).isFalse();
    }

    private static X509Certificate selfSigned(String dn) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        X500Name name = new X500Name(dn);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(1),
                Date.from(now), Date.from(now.plusSeconds(3600L * 24 * 365)),
                name, kp.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }
}
