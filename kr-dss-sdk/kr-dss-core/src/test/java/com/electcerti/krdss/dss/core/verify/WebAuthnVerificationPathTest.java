package com.electcerti.krdss.dss.core.verify;

import com.electcerti.krdss.ades.cades.bind.HashSuite;
import com.electcerti.krdss.ades.cades.bind.SignedAttrsBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 특허-A WebAuthn 검증 경로 — 합성 어서션으로 COSE 서명 실검증(webauthn4j) 확인.
 */
class WebAuthnVerificationPathTest {

    private static final String RP_ID = "localhost";
    private static final String ORIGIN = "http://localhost:8080";

    private final WebAuthnVerificationPath path = new WebAuthnVerificationPath();

    @Test
    void valid_assertion_passes() throws Exception {
        Fixture f = new Fixture();
        var input = f.input(f.clientDataJSON, f.signature);
        var result = path.verify(input, f.stored);
        assertThat(result.ok()).as(result.reason()).isTrue();
        assertThat(result.observedSignCount()).isEqualTo(1L);
    }

    @Test
    void tampered_signature_fails() throws Exception {
        Fixture f = new Fixture();
        byte[] badSig = f.signature.clone();
        badSig[badSig.length - 1] ^= 0x01;
        var result = path.verify(f.input(f.clientDataJSON, badSig), f.stored);
        assertThat(result.ok()).isFalse();
    }

    @Test
    void wrong_challenge_fails() throws Exception {
        Fixture f = new Fixture();
        // 다른 문서로 만든 clientDataJSON(결속 challenge 불일치) — 서명도 그에 맞게 재생성
        byte[] otherDocDigest = sha256("다른 문서");
        var otherAttrs = SignedAttrsBuilder.build(otherDocDigest, f.signingTime, f.cert, HashSuite.SHA_256);
        String otherChallenge = b64url(HashSuite.SHA_256.digest(otherAttrs.der()));
        byte[] otherClientData = clientDataJSON(otherChallenge);
        byte[] otherSig = f.sign(f.authData, otherClientData);
        // 검증은 원래 SignedAttrs(f) 기준 → challenge 불일치로 실패
        var result = path.verify(f.input(otherClientData, otherSig), f.stored);
        assertThat(result.ok()).isFalse();
    }

    @Test
    void disallowed_origin_fails() throws Exception {
        Fixture f = new Fixture();
        var input = new WebAuthnVerificationPath.Input(
                f.signedAttrs.der(), HashSuite.SHA_256, f.credentialId,
                f.authData, f.clientDataJSON, f.signature,
                RP_ID, List.of("http://evil.example"), false);
        var result = path.verify(input, f.stored);
        assertThat(result.ok()).isFalse();
    }

    @Test
    void unknown_credential_fails() {
        var input = new WebAuthnVerificationPath.Input(
                new byte[]{1}, HashSuite.SHA_256, new byte[]{2},
                new byte[]{3}, new byte[]{4}, new byte[]{5},
                RP_ID, List.of(ORIGIN), false);
        assertThat(path.verify(input, null).ok()).isFalse();
    }

    // --- 합성 어서션 생성기 ---

    private final class Fixture {
        final KeyPair kp;
        final X509Certificate cert;
        final byte[] credentialId = "demo-cred-id".getBytes(StandardCharsets.UTF_8);
        final Instant signingTime = Instant.parse("2026-06-28T00:00:00Z");
        final SignedAttrsBuilder.SignedAttrs signedAttrs;
        final byte[] authData;
        final byte[] clientDataJSON;
        final byte[] signature;
        final WebAuthnCredentialStore.StoredCredential stored;

        Fixture() throws Exception {
            kp = ecKeyPair();
            cert = selfSigned(kp, "CN=KR-DSS WebAuthn Signer");
            signedAttrs = SignedAttrsBuilder.build(sha256("계약서 본문"), signingTime, cert, HashSuite.SHA_256);
            String challenge = b64url(HashSuite.SHA_256.digest(signedAttrs.der()));
            clientDataJSON = clientDataJSON(challenge);
            authData = authenticatorData(RP_ID, (byte) 0x05, 1); // UP|UV, signCount=1
            signature = sign(authData, clientDataJSON);
            stored = new WebAuthnCredentialStore.StoredCredential(cert, -7, new byte[16], 0L);
        }

        byte[] sign(byte[] authenticatorData, byte[] clientData) throws Exception {
            byte[] base = concat(authenticatorData, sha256Bytes(clientData));
            Signature s = Signature.getInstance("SHA256withECDSA");
            s.initSign(kp.getPrivate());
            s.update(base);
            return s.sign();
        }

        WebAuthnVerificationPath.Input input(byte[] clientData, byte[] sig) {
            return new WebAuthnVerificationPath.Input(
                    signedAttrs.der(), HashSuite.SHA_256, credentialId,
                    authData, clientData, sig, RP_ID, List.of(ORIGIN), false);
        }
    }

    private static byte[] clientDataJSON(String challengeB64Url) {
        String json = "{\"type\":\"webauthn.get\",\"challenge\":\"" + challengeB64Url
                + "\",\"origin\":\"" + ORIGIN + "\",\"crossOrigin\":false}";
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] authenticatorData(String rpId, byte flags, int signCount) throws Exception {
        byte[] rpIdHash = sha256Bytes(rpId.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(rpIdHash);
        out.write(flags);
        out.write((signCount >>> 24) & 0xff);
        out.write((signCount >>> 16) & 0xff);
        out.write((signCount >>> 8) & 0xff);
        out.write(signCount & 0xff);
        return out.toByteArray();
    }

    private static KeyPair ecKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        return kpg.generateKeyPair();
    }

    private static X509Certificate selfSigned(KeyPair kp, String dn) throws Exception {
        X500Name name = new X500Name(dn);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var builder = new JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(1),
                java.util.Date.from(now), java.util.Date.from(now.plusSeconds(3600L * 24 * 365)),
                name, kp.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(kp.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static byte[] sha256(String s) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256Bytes(byte[] b) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(b);
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static String b64url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
