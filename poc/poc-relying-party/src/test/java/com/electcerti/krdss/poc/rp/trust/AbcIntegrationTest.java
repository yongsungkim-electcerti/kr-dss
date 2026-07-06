package com.electcerti.krdss.poc.rp.trust;

import com.electcerti.krdss.dss.api.VerificationStatus;
import com.electcerti.krdss.dss.core.verify.VerificationResult;
import com.electcerti.krdss.dss.pki.AuthenticatorGrade;
import com.electcerti.krdss.dss.trust.AuthenticatorTrustEntry;
import com.electcerti.krdss.dss.trust.DeviceTrustList;
import com.electcerti.krdss.dss.trust.DeviceType;
import com.electcerti.krdss.dss.trust.IntegratedTrustEvaluator;
import com.electcerti.krdss.dss.trust.IntegratedTrustVerifier;
import com.electcerti.krdss.dss.trust.KrIntegratedTrustList;
import com.electcerti.krdss.dss.trust.PolicyAutoUpdater;
import com.electcerti.krdss.dss.trust.TrustListUpdateEvent;
import com.electcerti.krdss.dss.trust.TrustPolicy;
import com.electcerti.krdss.dss.trust.TrustServiceRegistry;
import com.electcerti.krdss.poc.rp.local.Mode1LocalSignService;
import com.electcerti.krdss.poc.rp.local.WebAuthnDemoCa;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;
import org.junit.jupiter.api.Test;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
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
 * A/B/C 통합 — 특허-A(Mode1 서명·검증) + 특허-B(CA 발급) + 특허-C(통합 신뢰목록)를 묶어,
 * 신뢰목록의 장치 폐지(정책 자동 갱신)가 동일 서명의 검증 결과를 자동으로 뒤집는지 확인한다.
 */
class AbcIntegrationTest {

    private static final String RP_ID = "localhost";
    private static final String ORIGIN = "http://localhost:8080";

    private record Harness(Mode1LocalSignService svc, PolicyAutoUpdater updater, byte[] aaguid) {
    }

    /** 데모 CA(발급기관)와 데모 인증기를 등재한 신뢰목록으로 Mode1 서비스를 구성. */
    private Harness harness() {
        WebAuthnDemoCa demoCa = new WebAuthnDemoCa(); // 자가서명 데모 CA
        String caCn = cn(demoCa.caCertificate().getSubjectX500Principal().getName());
        byte[] demoAaguid = new byte[16]; // attestation none → AAGUID 전부 0

        var services = new TrustServiceRegistry().register(
                new TrustServiceRegistry.TrustServiceEntry(caCn, "CA", ServiceStatus.GRANTED, "KISA"));
        var devices = new DeviceTrustList().registerAuthenticator(new AuthenticatorTrustEntry(
                demoAaguid, "KR-DSS", "Passkey", "SE", "L2", "none",
                null, AuthenticatorGrade.HIGH, ServiceStatus.GRANTED));
        var tl = new KrIntegratedTrustList(services, devices, "KISA");
        var evaluator = new IntegratedTrustEvaluator(new IntegratedTrustVerifier(tl, new TrustPolicy()));

        var svc = new Mode1LocalSignService(demoCa, evaluator, RP_ID, ORIGIN, false, 120, "SHA_256", "cms");
        return new Harness(svc, new PolicyAutoUpdater(tl), demoAaguid);
    }

    @Test
    void signed_then_trustlist_revocation_flips_verdict() throws Exception {
        Harness h = harness();
        KeyPair passkey = ecKeyPair();
        String credId = b64url("abc-cred".getBytes(StandardCharsets.UTF_8));

        // A: 등록(B의 CA 발급) → begin → 어서션 → finish
        h.svc().register(passkey.getPublic().getEncoded(), credId, -7, null);
        byte[] document = "A/B/C 통합 계약서".getBytes(StandardCharsets.UTF_8);
        var begin = h.svc().begin(document, credId);
        var a = makeAssertion(passkey, begin.challenge());
        var finish = h.svc().finish(begin.ticket(), credId, a.clientDataJSON, a.authData, a.signature);
        byte[] container = Base64.getDecoder().decode(finish.containerB64());

        // C: 신뢰목록 정상 → 통합 검증 TOTAL_PASSED
        assertThat(h.svc().verify(container, document).indication())
                .as("신뢰목록 정상")
                .isEqualTo(VerificationStatus.TOTAL_PASSED);

        // 정책 자동 갱신: 장치 폐지 이벤트
        h.updater().apply(TrustListUpdateEvent.deviceRevoked(DeviceType.WEBAUTHN, h.aaguid()));

        // 동일 서명이지만 신뢰목록 폐지 → 통합 검증 TOTAL_FAILED
        VerificationResult after = h.svc().verify(container, document);
        assertThat(after.indication()).as("장치 폐지 후").isEqualTo(VerificationStatus.TOTAL_FAILED);
        assertThat(after.subIndication()).isEqualTo("TRUST_DEVICE_OR_CA_REVOKED");
    }

    // --- helpers ---

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
        return new Assertion(clientDataJSON, authData, s.sign());
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

    private static String cn(String dn) {
        try {
            for (Rdn rdn : new LdapName(dn).getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return String.valueOf(rdn.getValue());
                }
            }
        } catch (Exception ignored) {
            // 전체 DN 반환
        }
        return dn;
    }
}
