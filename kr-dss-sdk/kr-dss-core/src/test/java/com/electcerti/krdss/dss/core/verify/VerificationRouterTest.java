package com.electcerti.krdss.dss.core.verify;

import com.electcerti.krdss.ades.cades.bind.HashSuite;
import com.electcerti.krdss.ades.cades.bind.SignedAttrsBuilder;
import com.electcerti.krdss.ades.cades.container.WebAuthnAssertionAttr;
import com.electcerti.krdss.ades.cades.container.WebAuthnCmsAssembler;
import com.electcerti.krdss.dss.api.VerificationStatus;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
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
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 특허-A T5 — 정책 기반 검증 라우터 E2E(WebAuthn 결속 컨테이너 + HSM 위임 + 3분류).
 */
class VerificationRouterTest {

    private static final String RP_ID = "localhost";
    private static final String ORIGIN = "http://localhost:8080";

    private final VerificationRouter router = new VerificationRouter();
    private final WebAuthnCmsAssembler assembler = new WebAuthnCmsAssembler();

    @Test
    void webauthn_registered_valid_document_total_passed() throws Exception {
        Fixture f = new Fixture("전자계약서 본문");
        WebAuthnCredentialStore store = new WebAuthnCredentialStore();
        store.put(b64url(f.credentialId),
                new WebAuthnCredentialStore.StoredCredential(f.cert, -7, new byte[16], 0L));

        VerificationResult result = router.verify(f.container, f.document, VerificationRouter.Policy.demo(), store);

        assertThat(result.signaturePath()).isEqualTo("WEBAUTHN");
        assertThat(result.indication()).as(result.subIndication()).isEqualTo(VerificationStatus.TOTAL_PASSED);
    }

    @Test
    void webauthn_tampered_document_total_failed() throws Exception {
        Fixture f = new Fixture("원본 문서");
        WebAuthnCredentialStore store = new WebAuthnCredentialStore();
        store.put(b64url(f.credentialId),
                new WebAuthnCredentialStore.StoredCredential(f.cert, -7, new byte[16], 0L));

        byte[] tampered = "변조된 문서".getBytes(StandardCharsets.UTF_8);
        VerificationResult result = router.verify(f.container, tampered, VerificationRouter.Policy.demo(), store);

        assertThat(result.indication()).isEqualTo(VerificationStatus.TOTAL_FAILED);
        assertThat(result.subIndication()).isEqualTo("HASH_FAILURE");
    }

    @Test
    void webauthn_unregistered_credential_indeterminate() throws Exception {
        Fixture f = new Fixture("전자계약서 본문");
        WebAuthnCredentialStore emptyStore = new WebAuthnCredentialStore();

        VerificationResult result = router.verify(f.container, f.document, VerificationRouter.Policy.demo(), emptyStore);

        // 암호검증은 통과(컨테이너 인증서)하나 미등록 → INDETERMINATE
        assertThat(result.indication()).isEqualTo(VerificationStatus.INDETERMINATE);
        assertThat(result.subIndication()).isEqualTo("CREDENTIAL_NOT_REGISTERED");
    }

    @Test
    void non_webauthn_container_routes_to_hsm_path() {
        byte[] json = "{\"format\":\"MADES\",\"signatureMode\":\"REMOTE\"}".getBytes(StandardCharsets.UTF_8);
        VerificationResult result = router.verify(json, null, VerificationRouter.Policy.demo(),
                new WebAuthnCredentialStore());
        assertThat(result.signaturePath()).isEqualTo("HSM");
    }

    // --- 합성 WebAuthn 결속 컨테이너 생성기 ---

    private final class Fixture {
        final KeyPair kp;
        final X509Certificate cert;
        final byte[] credentialId = "router-cred-id".getBytes(StandardCharsets.UTF_8);
        final byte[] document;
        final byte[] container;

        Fixture(String docText) throws Exception {
            kp = ecKeyPair();
            cert = selfSigned(kp, "CN=KR-DSS Router Signer");
            document = docText.getBytes(StandardCharsets.UTF_8);

            byte[] docDigest = sha256(document);
            SignedAttrsBuilder.SignedAttrs attrs = SignedAttrsBuilder.build(
                    docDigest, Instant.parse("2026-06-28T00:00:00Z"), cert, HashSuite.SHA_256);

            String challenge = b64url(HashSuite.SHA_256.digest(attrs.der()));
            byte[] clientDataJSON = ("{\"type\":\"webauthn.get\",\"challenge\":\"" + challenge
                    + "\",\"origin\":\"" + ORIGIN + "\",\"crossOrigin\":false}")
                    .getBytes(StandardCharsets.UTF_8);
            byte[] authData = authenticatorData(RP_ID, (byte) 0x05, 1);
            byte[] signature = signEs256(kp, authData, clientDataJSON);

            WebAuthnAssertionAttr attr = WebAuthnAssertionAttr.of(
                    authData, clientDataJSON, -7, credentialId, new byte[16]);
            container = assembler.assemble(attrs.der(), signature, List.of(cert), attr);
        }
    }

    private static byte[] signEs256(KeyPair kp, byte[] authData, byte[] clientDataJSON) throws Exception {
        byte[] base = concat(authData, sha256(clientDataJSON));
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(kp.getPrivate());
        s.update(base);
        return s.sign();
    }

    private static byte[] authenticatorData(String rpId, byte flags, int signCount) throws Exception {
        byte[] rpIdHash = sha256(rpId.getBytes(StandardCharsets.UTF_8));
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
                Date.from(now), Date.from(now.plusSeconds(3600L * 24 * 365)),
                name, kp.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(kp.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private static byte[] sha256(byte[] b) throws Exception {
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
