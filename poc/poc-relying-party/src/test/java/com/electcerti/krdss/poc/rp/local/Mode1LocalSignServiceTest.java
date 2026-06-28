package com.electcerti.krdss.poc.rp.local;

import com.electcerti.krdss.dss.api.VerificationStatus;
import com.electcerti.krdss.dss.core.verify.VerificationResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 특허-A T6 — Mode 1(WebAuthn 로컬 서명) end-to-end: 등록→begin→finish→verify.
 *
 * <p>브라우저 인증기를 합성(EC P-256)으로 대체해 서비스 계층 전 구간을 구동한다.</p>
 */
class Mode1LocalSignServiceTest {

    private static final String ORIGIN = "http://localhost:8080";
    private static final String RP_ID = "localhost";

    private Mode1LocalSignService newService() {
        return new Mode1LocalSignService(new WebAuthnDemoCa(), RP_ID, ORIGIN, false, 120);
    }

    @Test
    void full_flow_total_passed() throws Exception {
        Mode1LocalSignService svc = newService();
        KeyPair passkey = ecKeyPair();
        String credIdB64 = b64url("passkey-cred-1".getBytes(StandardCharsets.UTF_8));

        // 1) 등록 — 공개키(SPKI)로 CA 인증서 발급·저장
        var reg = svc.register(passkey.getPublic().getEncoded(), credIdB64, -7, null);
        assertThat(reg.certificatePem()).contains("BEGIN CERTIFICATE");
        assertThat(reg.coseAlg()).isEqualTo("ES256");

        // 2) begin — 결속 challenge 발급
        byte[] document = "전자계약서: 갑과 을은 동의한다".getBytes(StandardCharsets.UTF_8);
        var begin = svc.begin(document, credIdB64);
        assertThat(begin.challenge()).isNotBlank();
        assertThat(begin.rpId()).isEqualTo(RP_ID);

        // 3) 인증기 어서션 합성 (challenge 에 서명)
        Assertion a = makeAssertion(passkey, begin.challenge());

        // 4) finish — 컨테이너 조립 + 라우터 검증
        var finish = svc.finish(begin.ticket(), credIdB64, a.clientDataJSON, a.authData, a.signature);
        assertThat(finish.report().indication())
                .as(finish.report().subIndication())
                .isEqualTo(VerificationStatus.TOTAL_PASSED);
        assertThat(finish.report().signaturePath()).isEqualTo("WEBAUTHN");

        // 5) verify — 컨테이너 재검증(원문 일치)
        VerificationResult re = svc.verify(Base64.getDecoder().decode(finish.containerB64()), document);
        assertThat(re.indication()).isEqualTo(VerificationStatus.TOTAL_PASSED);
    }

    @Test
    void verify_with_tampered_document_total_failed() throws Exception {
        Mode1LocalSignService svc = newService();
        KeyPair passkey = ecKeyPair();
        String credIdB64 = b64url("passkey-cred-2".getBytes(StandardCharsets.UTF_8));
        svc.register(passkey.getPublic().getEncoded(), credIdB64, -7, null);

        byte[] document = "원본 문서".getBytes(StandardCharsets.UTF_8);
        var begin = svc.begin(document, credIdB64);
        Assertion a = makeAssertion(passkey, begin.challenge());
        var finish = svc.finish(begin.ticket(), credIdB64, a.clientDataJSON, a.authData, a.signature);
        assertThat(finish.report().indication()).isEqualTo(VerificationStatus.TOTAL_PASSED);

        // 변조된 원문으로 검증 → 문서 무결성 실패
        VerificationResult re = svc.verify(
                Base64.getDecoder().decode(finish.containerB64()),
                "변조된 문서".getBytes(StandardCharsets.UTF_8));
        assertThat(re.indication()).isEqualTo(VerificationStatus.TOTAL_FAILED);
        assertThat(re.subIndication()).isEqualTo("HASH_FAILURE");
    }

    // --- 합성 인증기 ---

    private record Assertion(byte[] clientDataJSON, byte[] authData, byte[] signature) {
    }

    private Assertion makeAssertion(KeyPair passkey, String challenge) throws Exception {
        byte[] clientDataJSON = ("{\"type\":\"webauthn.get\",\"challenge\":\"" + challenge
                + "\",\"origin\":\"" + ORIGIN + "\",\"crossOrigin\":false}").getBytes(StandardCharsets.UTF_8);
        byte[] authData = authenticatorData((byte) 0x05, 1);
        byte[] base = concat(authData, sha256(clientDataJSON));
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(passkey.getPrivate());
        s.update(base);
        byte[] signature = s.sign();
        return new Assertion(clientDataJSON, authData, signature);
    }

    private static byte[] authenticatorData(byte flags, int signCount) throws Exception {
        byte[] rpIdHash = sha256(RP_ID.getBytes(StandardCharsets.UTF_8));
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
