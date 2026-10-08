package com.electcerti.krdss.poc.rp.local;

import com.electcerti.krdss.ades.cades.bind.HashSuite;
import com.electcerti.krdss.ades.cades.bind.SignatureBindingService;
import com.electcerti.krdss.ades.cades.bind.SignedAttrsBuilder;
import com.electcerti.krdss.ades.cades.container.WebAuthnAssertionAttr;
import com.electcerti.krdss.ades.cades.container.WebAuthnCmsAssembler;
import com.electcerti.krdss.ades.cades.container.WebAuthnCmsSignedData;
import com.electcerti.krdss.dss.api.TrustListEvaluator;
import com.electcerti.krdss.dss.core.verify.VerificationResult;
import com.electcerti.krdss.dss.core.verify.VerificationRouter;
import com.electcerti.krdss.dss.core.verify.WebAuthnCredentialStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.Extension;
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
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
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

    private static final Logger log = LoggerFactory.getLogger(Mode1LocalSignService.class);

    private final WebAuthnDemoCa ca;
    private final WebAuthnCredentialStore store = new WebAuthnCredentialStore();
    private final WebAuthnCmsAssembler assembler = new WebAuthnCmsAssembler();
    private final WebAuthnCmsSignedData cmsAssembler = new WebAuthnCmsSignedData();
    private final VerificationRouter router;
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    private final String rpId;
    private final List<String> allowedOrigins;
    private final boolean userVerificationRequired;
    private final long challengeTtlMs;
    private final HashSuite hashSuite;
    /** 결속 컨테이너 포맷: {@code cms}(정식 RFC5652 SignedData, 기본) | {@code mock}(모사 DER). */
    private final String containerFormat;

    public Mode1LocalSignService(
            WebAuthnDemoCa ca,
            TrustListEvaluator trustEvaluator,
            @Value("${krdss.rp.mode1.rp-id:localhost}") String rpId,
            @Value("${krdss.rp.mode1.allowed-origins:http://localhost:8080}") String allowedOrigins,
            @Value("${krdss.rp.mode1.user-verification-required:false}") boolean userVerificationRequired,
            @Value("${krdss.rp.mode1.challenge-ttl-seconds:120}") long challengeTtlSeconds,
            @Value("${krdss.rp.mode1.hash-suite:SHA_256}") String hashSuite,
            @Value("${krdss.rp.mode1.container-format:cms}") String containerFormat) {
        this.ca = ca;
        // 특허-C 통합 신뢰목록 평가기를 라우터에 주입(A/B/C). null 이면 신뢰목록 미평가.
        this.router = new VerificationRouter(trustEvaluator);
        this.rpId = rpId;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList();
        this.userVerificationRequired = userVerificationRequired;
        this.challengeTtlMs = challengeTtlSeconds * 1000;
        this.hashSuite = HashSuite.valueOf(hashSuite.trim());
        this.containerFormat = containerFormat.trim().toLowerCase();
    }

    private record Pending(byte[] document, byte[] signedAttrsDer, String credentialIdB64, long expiresAt) {
    }

    public record RegisterResult(String credentialId, String certificatePem, String coseAlg) {
    }

    public record CertificateDetail(
            int chainIndex, String role, String subject, String issuer, String serialNumber,
            String notBefore, String notAfter, String signatureAlgorithm,
            String publicKeyAlgorithm, String publicKeyFormat, String sha256Fingerprint,
            boolean ca, List<String> policyOids, String certificatePem) {
    }

    public record CertificateLookupResult(
            String credentialId, String coseAlg, int chainLength, List<CertificateDetail> chain) {
    }

    public record CertificateSummary(
            String credentialId, String subject, String issuer, String serialNumber,
            String notAfter, String coseAlg) {
    }

    public record BeginResult(String ticket, String challenge, String rpId,
                              List<String> allowCredentials, long timeoutMs) {
    }

    /**
     * 브라우저가 등록·서명에서 공통으로 사용할 WebAuthn 파라미터.
     *
     * <p>등록({@code create()})과 서명({@code get()})의 rpId 출처를 서버 설정 하나로 통일하기 위한
     * 값이다. 과거에는 등록만 브라우저의 {@code location.hostname} 을 사용해, 접속 주소와
     * {@code krdss.rp.mode1.rp-id} 가 어긋나면 등록은 성공하지만 서명에서
     * "not a registrable domain suffix" 오류가 발생했다.</p>
     */
    public record WebAuthnConfig(String rpId, List<String> allowedOrigins,
                                 boolean userVerificationRequired) {
    }

    /** 등록·서명 공통 WebAuthn 파라미터를 노출한다. */
    public WebAuthnConfig webAuthnConfig() {
        return new WebAuthnConfig(rpId, allowedOrigins, userVerificationRequired);
    }

    public record FinishResult(String containerB64, VerificationResult report,
                               CertificateDetail signerCertificate) {
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
        log.info("[Mode1] 등록 cred={} alg={} → CA 인증서 발급·저장(subject={})",
                shortId(credentialIdB64), coseAlgName(effectiveAlg),
                cert.getSubjectX500Principal().getName());
        return new RegisterResult(credentialIdB64, toPem(cert), coseAlgName(effectiveAlg));
    }

    /** PC 화면에서 Credential ID로 사용자 인증서와 전체 발급 체인을 조회한다. */
    public CertificateLookupResult certificate(String credentialIdB64) {
        WebAuthnCredentialStore.StoredCredential credential = store.find(credentialIdB64)
                .orElseThrow(() -> new IllegalArgumentException("등록되지 않은 자격증명"));
        List<X509Certificate> certificates = new java.util.ArrayList<>();
        certificates.add(credential.certificate());
        certificates.addAll(ca.caChain());
        List<CertificateDetail> chain = new java.util.ArrayList<>();
        for (int i = 0; i < certificates.size(); i++) {
            X509Certificate cert = certificates.get(i);
            String role = i == 0 ? "사용자(Tester)" :
                    (i == certificates.size() - 1 ? "Root CA" : "사업자 CA");
            chain.add(toDetail(i, role, cert));
        }
        return new CertificateLookupResult(
                credentialIdB64, coseAlgName(credential.coseAlg()), chain.size(), List.copyOf(chain));
    }

    /** PC에 등록된 인증서 선택 목록을 제공한다. */
    public List<CertificateSummary> certificates() {
        return store.entries().entrySet().stream()
                .map(entry -> {
                    X509Certificate cert = entry.getValue().certificate();
                    return new CertificateSummary(
                            entry.getKey(), cert.getSubjectX500Principal().getName(),
                            cert.getIssuerX500Principal().getName(),
                            cert.getSerialNumber().toString(16).toUpperCase(),
                            cert.getNotAfter().toInstant().toString(),
                            coseAlgName(entry.getValue().coseAlg()));
                })
                .sorted(Comparator.comparing(CertificateSummary::notAfter).reversed())
                .toList();
    }

    /** 서명 begin: 3요소 결속 SignedAttrs 구성 후 결속 challenge 를 발급한다. */
    public BeginResult begin(byte[] document, String credentialIdB64) {
        WebAuthnCredentialStore.StoredCredential cred = store.find(credentialIdB64)
                .orElseThrow(() -> new IllegalArgumentException("등록되지 않은 자격증명: 먼저 패스키를 등록하세요"));
        byte[] docDigest = hashSuite.digest(document);
        SignedAttrsBuilder.SignedAttrs signedAttrs = SignedAttrsBuilder.build(
                docDigest, Instant.now(), cred.certificate(), hashSuite);
        String challenge = SignatureBindingService.deriveChallenge(signedAttrs);

        String ticket = UUID.randomUUID().toString();
        pending.put(ticket, new Pending(document, signedAttrs.der(), credentialIdB64,
                System.currentTimeMillis() + challengeTtlMs));
        log.info("[Mode1] begin cred={} docBytes={} hash={} → 결속 challenge={}…(SignedAttrs {}B)",
                shortId(credentialIdB64), document.length, hashSuite,
                challenge.substring(0, Math.min(12, challenge.length())), signedAttrs.der().length);
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
        byte[] container = assembleContainer(p.signedAttrsDer(), signature, cred.certificate(), attr);

        VerificationResult report = router.verify(container, p.document(), policy(), store, hashSuite);
        logReport("finish[" + containerFormat + "/" + container.length + "B]", shortId(p.credentialIdB64()), report);
        return new FinishResult(Base64.getEncoder().encodeToString(container), report,
                toDetail(0, "사용자(Tester)", cred.certificate()));
    }

    /** 검증: 결속 컨테이너(Base64)를 정책 라우터로 검증한다. */
    public VerificationResult verify(byte[] container, byte[] originalDocument) {
        VerificationResult report = router.verify(container, originalDocument, policy(), store, hashSuite);
        logReport("verify", "container(" + container.length + "B)", report);
        return report;
    }

    /** 서명 컨테이너에 포함된 사용자 인증서 상세정보. */
    public CertificateDetail signerCertificate(byte[] container) {
        WebAuthnCmsAssembler.Parsed parsed = "mock".equals(containerFormat)
                ? assembler.parse(container) : cmsAssembler.parse(container);
        if (parsed.certificates() == null || parsed.certificates().isEmpty()) {
            throw new IllegalArgumentException("서명 컨테이너에 사용자 인증서가 없습니다");
        }
        return toDetail(0, "사용자(Tester)", parsed.certificates().get(0));
    }

    private void logReport(String phase, String who, VerificationResult report) {
        log.info("[Mode1] {} {} → {}{} path={}",
                phase, who, report.indication(),
                report.subIndication() != null ? "(" + report.subIndication() + ")" : "",
                report.signaturePath());
        if (report.checks() != null) {
            for (VerificationResult.Check c : report.checks()) {
                log.debug("[Mode1]   {} {} — {}", c.passed() ? "PASS" : "FAIL", c.name(), c.message());
            }
        }
    }

    /** 설정된 포맷으로 결속 컨테이너를 조립한다(정식 CMS 기본, 모사 fallback). */
    private byte[] assembleContainer(byte[] signedAttrsDer, byte[] signature,
                                     X509Certificate cert, WebAuthnAssertionAttr attr) {
        if ("mock".equals(containerFormat)) {
            return assembler.assemble(signedAttrsDer, signature, List.of(cert), attr);
        }
        return cmsAssembler.assemble(signedAttrsDer, hashSuite, signature, List.of(cert), attr);
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

    private static String shortId(String credentialIdB64) {
        if (credentialIdB64 == null) {
            return "(null)";
        }
        return credentialIdB64.length() <= 12 ? credentialIdB64 : credentialIdB64.substring(0, 12) + "…";
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

    private static CertificateDetail toDetail(int index, String role, X509Certificate cert) {
        try {
            String fingerprint = HexFormat.ofDelimiter(":").withUpperCase()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
            return new CertificateDetail(
                    index, role,
                    cert.getSubjectX500Principal().getName(),
                    cert.getIssuerX500Principal().getName(),
                    cert.getSerialNumber().toString(16).toUpperCase(),
                    cert.getNotBefore().toInstant().toString(),
                    cert.getNotAfter().toInstant().toString(),
                    cert.getSigAlgName(), cert.getPublicKey().getAlgorithm(),
                    cert.getPublicKey().getFormat(), fingerprint,
                    cert.getBasicConstraints() >= 0, policyOids(cert), toPem(cert));
        } catch (Exception e) {
            throw new IllegalStateException("인증서 상세정보 생성 실패", e);
        }
    }

    private static List<String> policyOids(X509Certificate cert) {
        try {
            byte[] extension = cert.getExtensionValue(Extension.certificatePolicies.getId());
            if (extension == null) {
                return List.of();
            }
            byte[] value = ASN1OctetString.getInstance(extension).getOctets();
            CertificatePolicies policies = CertificatePolicies.getInstance(
                    ASN1Primitive.fromByteArray(value));
            return Arrays.stream(policies.getPolicyInformation())
                    .map(policy -> policy.getPolicyIdentifier().getId())
                    .toList();
        } catch (Exception e) {
            log.warn("인증서 정책 OID 파싱 실패(subject={}): {}",
                    cert.getSubjectX500Principal().getName(), e.getMessage());
            return List.of();
        }
    }
}
