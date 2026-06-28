package com.electcerti.krdss.poc.rp.local;

import com.electcerti.krdss.ades.cades.bind.HashSuite;
import com.electcerti.krdss.ades.cades.bind.SignatureBindingService;
import com.electcerti.krdss.ades.cades.bind.SignedAttrsBuilder;
import com.electcerti.krdss.ades.cades.container.WebAuthnAssertionAttr;
import com.electcerti.krdss.ades.cades.container.WebAuthnCmsAssembler;
import com.electcerti.krdss.dss.core.verify.VerificationResult;
import com.electcerti.krdss.dss.core.verify.VerificationRouter;
import com.electcerti.krdss.dss.core.verify.WebAuthnCredentialStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 특허-A Mode 1 — WebAuthn 로컬 서명 오케스트레이션(SAM/HSM 미경유, 설계서 §2.4).
 *
 * <p>등록(CA 인증서 발급·저장) → 서명 begin(결속 challenge 발급) → 서명 finish(어서션→결속
 * 컨테이너 조립→정책 라우터 검증)을 담당한다. 어서션 자체가 전자서명이며, 검증용 공개키는
 * 등록 시 발급한 CA 인증서에서 가져온다.</p>
 */
@Service
public class Mode1LocalSignService {

    private final WebAuthnDemoCa ca;
    private final WebAuthnCredentialStore store = new WebAuthnCredentialStore();
    private final WebAuthnCmsAssembler assembler = new WebAuthnCmsAssembler();
    private final VerificationRouter router = new VerificationRouter();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    private final String rpId;
    private final List<String> allowedOrigins;
    private final boolean userVerificationRequired;
    private final long challengeTtlMs;

    public Mode1LocalSignService(
            WebAuthnDemoCa ca,
            @Value("${krdss.rp.mode1.rp-id:localhost}") String rpId,
            @Value("${krdss.rp.mode1.allowed-origins:http://localhost:8080}") String allowedOrigins,
            @Value("${krdss.rp.mode1.user-verification-required:false}") boolean userVerificationRequired,
            @Value("${krdss.rp.mode1.challenge-ttl-seconds:120}") long challengeTtlSeconds) {
        this.ca = ca;
        this.rpId = rpId;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList();
        this.userVerificationRequired = userVerificationRequired;
        this.challengeTtlMs = challengeTtlSeconds * 1000;
    }

    private record Pending(byte[] document, byte[] signedAttrsDer, String credentialIdB64, long expiresAt) {
    }

    public record RegisterResult(String credentialId, String certificatePem, String coseAlg) {
    }

    public record BeginResult(String ticket, String challenge, String rpId,
                              List<String> allowCredentials, long timeoutMs) {
    }

    public record FinishResult(String containerB64, VerificationResult report) {
    }

    /** 등록: Credential 공개키로 CA 인증서를 발급하고 레지스트리에 저장한다. */
    public RegisterResult register(byte[] spki, String credentialIdB64, int coseAlg, byte[] aaguid) {
        PublicKey publicKey = parsePublicKey(spki);
        byte[] credentialId = b64urlDecode(credentialIdB64);
        X509Certificate cert = ca.issue(publicKey, "KR-DSS WebAuthn Signer", credentialId);
        int effectiveAlg = coseAlg != 0 ? coseAlg
                : ("EC".equals(publicKey.getAlgorithm()) ? -7 : -257);
        store.put(credentialIdB64, new WebAuthnCredentialStore.StoredCredential(
                cert, effectiveAlg, aaguid != null ? aaguid : new byte[16], 0L));
        return new RegisterResult(credentialIdB64, toPem(cert), coseAlgName(effectiveAlg));
    }

    /** 서명 begin: 3요소 결속 SignedAttrs 구성 후 결속 challenge 를 발급한다. */
    public BeginResult begin(byte[] document, String credentialIdB64) {
        WebAuthnCredentialStore.StoredCredential cred = store.find(credentialIdB64)
                .orElseThrow(() -> new IllegalArgumentException("등록되지 않은 자격증명: 먼저 패스키를 등록하세요"));
        byte[] docDigest = sha256(document);
        SignedAttrsBuilder.SignedAttrs signedAttrs = SignedAttrsBuilder.build(
                docDigest, Instant.now(), cred.certificate(), HashSuite.SHA_256);
        String challenge = SignatureBindingService.deriveChallenge(signedAttrs);

        String ticket = UUID.randomUUID().toString();
        pending.put(ticket, new Pending(document, signedAttrs.der(), credentialIdB64,
                System.currentTimeMillis() + challengeTtlMs));
        return new BeginResult(ticket, challenge, rpId, List.of(credentialIdB64), challengeTtlMs);
    }

    /** 서명 finish: 어서션→결속 컨테이너 조립→정책 라우터 검증. */
    public FinishResult finish(String ticket, String webauthnCredIdB64,
                               byte[] clientDataJSON, byte[] authenticatorData, byte[] signature) {
        Pending p = pending.remove(ticket);
        if (p == null) {
            throw new IllegalArgumentException("유효하지 않거나 만료된 서명 세션");
        }
        if (System.currentTimeMillis() > p.expiresAt()) {
            throw new IllegalArgumentException("서명 세션 만료");
        }
        WebAuthnCredentialStore.StoredCredential cred = store.find(p.credentialIdB64())
                .orElseThrow(() -> new IllegalArgumentException("자격증명 조회 실패"));

        byte[] credentialId = b64urlDecode(webauthnCredIdB64);
        WebAuthnAssertionAttr attr = WebAuthnAssertionAttr.of(
                authenticatorData, clientDataJSON, cred.coseAlg(), credentialId, cred.aaguid());
        byte[] container = assembler.assemble(p.signedAttrsDer(), signature, List.of(cred.certificate()), attr);

        VerificationResult report = router.verify(container, p.document(), policy(), store);
        return new FinishResult(Base64.getEncoder().encodeToString(container), report);
    }

    /** 검증: 결속 컨테이너(Base64)를 정책 라우터로 검증한다. */
    public VerificationResult verify(byte[] container, byte[] originalDocument) {
        return router.verify(container, originalDocument, policy(), store);
    }

    public WebAuthnCredentialStore store() {
        return store;
    }

    private VerificationRouter.Policy policy() {
        return new VerificationRouter.Policy(rpId, allowedOrigins, userVerificationRequired, false);
    }

    // --- helpers ---

    private PublicKey parsePublicKey(byte[] spki) {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(spki);
        for (String alg : new String[]{"EC", "RSA"}) {
            try {
                return KeyFactory.getInstance(alg).generatePublic(spec);
            } catch (Exception ignored) {
                // 다음 알고리즘 시도
            }
        }
        throw new IllegalArgumentException("공개키(SPKI) 파싱 실패 — EC/RSA 만 지원");
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 사용 불가", e);
        }
    }

    private static String coseAlgName(int coseAlg) {
        return switch (coseAlg) {
            case -7 -> "ES256";
            case -257 -> "RS256";
            default -> "COSE(" + coseAlg + ")";
        };
    }

    private static byte[] b64urlDecode(String s) {
        return Base64.getUrlDecoder().decode(s);
    }

    private static String toPem(X509Certificate cert) {
        try {
            String b64 = Base64.getEncoder().encodeToString(cert.getEncoded());
            StringBuilder sb = new StringBuilder("-----BEGIN CERTIFICATE-----\n");
            for (int i = 0; i < b64.length(); i += 64) {
                sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
            }
            return sb.append("-----END CERTIFICATE-----\n").toString();
        } catch (Exception e) {
            return "(인증서 인코딩 실패)";
        }
    }
}
