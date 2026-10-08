package com.electcerti.krdss.dss.core.verify;

import com.electcerti.krdss.ades.cades.bind.HashSuite;
import com.electcerti.krdss.ades.cades.bind.SignedAttrsBuilder;
import com.electcerti.krdss.ades.cades.container.WebAuthnAssertionAttr;
import com.electcerti.krdss.ades.cades.container.WebAuthnCmsAssembler;
import com.electcerti.krdss.dss.api.TrustListEvaluator;
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
 * A/B/C 연계 — 검증 라우터가 특허-C 신뢰목록 평가 결과를 3분류에 종합하는지 검증.
 *
 * <p>암호검증·결속·문서 무결성은 모두 통과(등록 자격증명)하도록 고정하고, 주입한
 * {@link TrustListEvaluator} 의 판정만 바꿔가며 최종 종합 판정을 확인한다.</p>
 */
class VerificationRouterTrustTest {

    private static final String RP_ID = "localhost";
    private static final String ORIGIN = "http://localhost:8080";

    private VerificationResult verifyWith(TrustListEvaluator evaluator) throws Exception {
        Fixture f = new Fixture("전자계약서 본문");
        WebAuthnCredentialStore store = new WebAuthnCredentialStore();
        store.put(b64url(f.credentialId),
                new WebAuthnCredentialStore.StoredCredential(f.cert, -7, new byte[16], 0L));
        return new VerificationRouter(evaluator)
                .verify(f.container, f.document, VerificationRouter.Policy.demo(), store);
    }

    private static TrustListEvaluator fixed(VerificationStatus status) {
        return query -> new TrustListEvaluator.Evaluation(status, "고정 판정: " + status);
    }

    @Test
    void trust_passed_yields_total_passed() throws Exception {
        assertThat(verifyWith(fixed(VerificationStatus.TOTAL_PASSED)).indication())
                .isEqualTo(VerificationStatus.TOTAL_PASSED);
    }

    @Test
    void trust_revoked_yields_total_failed() throws Exception {
        // 암호검증은 통과해도 신뢰목록이 장치/CA 폐지 → 종합 TOTAL_FAILED
        VerificationResult r = verifyWith(fixed(VerificationStatus.TOTAL_FAILED));
        assertThat(r.indication()).isEqualTo(VerificationStatus.TOTAL_FAILED);
        assertThat(r.subIndication()).isEqualTo("TRUST_DEVICE_OR_CA_REVOKED");
    }

    @Test
    void trust_indeterminate_yields_indeterminate() throws Exception {
        VerificationResult r = verifyWith(fixed(VerificationStatus.INDETERMINATE));
        assertThat(r.indication()).isEqualTo(VerificationStatus.INDETERMINATE);
        assertThat(r.subIndication()).isEqualTo("TRUST_GRADE_OR_UNREGISTERED");
    }

    @Test
    void receives_issuer_cn_and_aaguid_in_query() throws Exception {
        // 라우터가 발급기관 CN 과 AAGUID 를 질의에 담아 전달하는지 확인
        String[] seenCn = new String[1];
        byte[][] seenAaguid = new byte[1][];
        TrustListEvaluator probe = query -> {
            seenCn[0] = query.caServiceId();
            seenAaguid[0] = query.deviceId();
            return new TrustListEvaluator.Evaluation(VerificationStatus.TOTAL_PASSED, "probe");
        };
        verifyWith(probe);
        assertThat(seenCn[0]).isEqualTo("KR-DSS Router Signer"); // 자가서명 → issuer CN
        assertThat(seenAaguid[0]).hasSize(16);
    }

    // --- 합성 WebAuthn 결속 컨테이너 생성기 (VerificationRouterTest 와 동일 구조) ---

    private final WebAuthnCmsAssembler assembler = new WebAuthnCmsAssembler();

    private final class Fixture {
        final KeyPair kp;
        final X509Certificate cert;
        final byte[] credentialId = "router-trust-cred".getBytes(StandardCharsets.UTF_8);
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
