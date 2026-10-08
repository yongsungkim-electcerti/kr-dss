package com.electcerti.krdss.poc.rp;

import com.electcerti.krdss.ades.core.KrAdesFormat;
import com.electcerti.krdss.ades.core.KrAdesLevel;
import com.electcerti.krdss.ades.core.PackagingType;
import com.electcerti.krdss.dss.api.Dtos.SignRequest;
import com.electcerti.krdss.dss.api.Dtos.SignResult;
import com.electcerti.krdss.dss.core.remote.RemoteSignCoordinator;
import com.electcerti.krdss.dss.core.remote.RemoteSignCoordinator.DataToSign;
import com.electcerti.krdss.dss.core.remote.RemoteSignVerifier;
import com.electcerti.krdss.dss.remote.CscModels.AuthorizeBeginRequest;
import com.electcerti.krdss.dss.remote.CscModels.AuthorizeBeginResponse;
import com.electcerti.krdss.dss.remote.CscModels.AuthorizeFinishRequest;
import com.electcerti.krdss.dss.remote.CscModels.AuthorizeResponse;
import com.electcerti.krdss.dss.remote.CscModels.CredentialInfo;
import com.electcerti.krdss.dss.remote.CscModels.Oid;
import com.electcerti.krdss.dss.remote.CscModels.PasskeyRegisterRequest;
import com.electcerti.krdss.dss.remote.CscModels.PasskeyRegisterResponse;
import com.electcerti.krdss.dss.remote.CscModels.SignHashRequest;
import com.electcerti.krdss.dss.remote.CscModels.SignHashResponse;
import com.electcerti.krdss.dss.remote.RemoteSignClient;
import com.electcerti.krdss.dss.remote.RemoteSignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SIC 웹 데모 API.
 *
 * <p>브라우저(데모 화면)가 호출하는 엔드포인트. 인증서 발급 조회, WebAuthn 기반
 * 원격전자서명(2단계: begin → 브라우저 인증 → finish), 서명 검증을 제공한다.
 * 모든 RSSP 호출은 OAuth2 Bearer 로 서비스 인증된다.</p>
 *
 * <pre>
 *   브라우저 → SIC /api/* → RSSP(CSC, Bearer) → SAM → HSM
 * </pre>
 */
@RestController
@RequestMapping("/api")
public class RpWebController {

    private final RemoteSignClient rssp;
    private final RemoteSignCoordinator coordinator = new RemoteSignCoordinator();
    private final RemoteSignVerifier verifier = new RemoteSignVerifier();

    private final String clientId;
    private final String clientSecret;
    private final String signerId;
    private final String defaultCredentialId;

    /** 진행 중인 서명 세션(begin↔finish 연결). */
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    public RpWebController(
            @Value("${krdss.rp.rssp-base-url:http://localhost:8090}") String rsspBaseUrl,
            @Value("${krdss.rp.oauth.client-id:krdss-rp-client}") String clientId,
            @Value("${krdss.rp.oauth.client-secret:rp-client-secret}") String clientSecret,
            @Value("${krdss.rp.signer.id:signer-001}") String signerId,
            @Value("${krdss.rp.credential-id:krdss-remote-signer}") String defaultCredentialId) {
        this.rssp = new RemoteSignClient(rsspBaseUrl);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.signerId = signerId;
        this.defaultCredentialId = defaultCredentialId;
    }

    private record Pending(byte[] document, KrAdesCoreOptions options, byte[] signingCertificate,
                           String credentialId, String hashB64, DataToSign dts) {
    }

    private record KrAdesCoreOptions(KrAdesFormat format, KrAdesLevel level, PackagingType packaging) {
    }

    private void authenticateService() {
        rssp.obtainAccessToken(clientId, clientSecret);
    }

    // === 인증서 발급/조회 ===

    public record CertRequest(String credentialId) {
    }

    public record CertResponse(String credentialId, String description, String keyAlgo, int keyLen,
                               String certPem, String status) {
    }

    @PostMapping("/cert/issue")
    public CertResponse certIssue(@RequestBody(required = false) CertRequest req) {
        authenticateService();
        String credentialId = (req == null || req.credentialId() == null || req.credentialId().isBlank())
                ? defaultCredentialId : req.credentialId();
        CredentialInfo info = rssp.credentialInfo(credentialId);
        String pem = info.certChainB64() == null || info.certChainB64().isEmpty()
                ? null : toPem(info.certChainB64().get(0));
        return new CertResponse(info.credentialID(), info.description(), info.keyAlgo(), info.keyLen(), pem, info.status());
    }

    // === 패스키 등록 ===

    public record RegisterRequest(String credentialId) {
    }

    @PostMapping("/passkey/register")
    public PasskeyRegisterResponse passkeyRegister(@RequestBody RegisterRequest req) {
        authenticateService();
        if (req.credentialId() == null || req.credentialId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "credentialId 필요");
        }
        return rssp.registerPasskey(new PasskeyRegisterRequest(signerId, req.credentialId(), null));
    }

    // === 서명: begin (다이제스트 계산 + WebAuthn 챌린지 발급) ===

    public record SignBeginRequest(String text, String format, String level, String packaging) {
    }

    public record SignBeginResponse(String ticket, String digestB64,
                                    String challenge, String rpId, List<String> allowCredentials, long timeoutMs) {
    }

    @PostMapping("/sign/begin")
    public SignBeginResponse signBegin(@RequestBody SignBeginRequest req) {
        if (req.text() == null || req.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "원문(text)이 비어 있습니다");
        }
        authenticateService();
        KrAdesCoreOptions opts = new KrAdesCoreOptions(
                parse(KrAdesFormat.class, req.format(), KrAdesFormat.MADES),
                parse(KrAdesLevel.class, req.level(), KrAdesLevel.KR_B),
                parse(PackagingType.class, req.packaging(), PackagingType.ENVELOPING));

        byte[] document = req.text().getBytes(StandardCharsets.UTF_8);

        // 자격증명 인증서 조회(서명객체에 포함)
        CredentialInfo cred = rssp.credentialInfo(defaultCredentialId);
        if (cred.certChainB64() == null || cred.certChainB64().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "자격증명 인증서 없음");
        }
        byte[] cert = Base64.getDecoder().decode(cred.certChainB64().get(0));

        // 코어로 다이제스트 계산
        DataToSign dts = coordinator.prepare(new SignRequest(document, opts.format(), opts.level(), opts.packaging()));
        String hashB64 = Base64.getEncoder().encodeToString(dts.digest());

        // SAM 에 WebAuthn 챌린지 요청(해시 바인딩)
        AuthorizeBeginResponse begin = rssp.authorizeBegin(
                new AuthorizeBeginRequest(defaultCredentialId, List.of(hashB64), signerId));

        String ticket = UUID.randomUUID().toString();
        pending.put(ticket, new Pending(document, opts, cert, defaultCredentialId, hashB64, dts));
        return new SignBeginResponse(ticket, hashB64,
                begin.challenge(), begin.rpId(), begin.allowCredentials(), begin.timeoutMs());
    }

    // === 서명: finish (WebAuthn 어서션 → SAD → signHash → 서명객체) ===

    public record SignFinishRequest(String ticket, String pin,
                                    String webauthnCredId, String clientDataJSON,
                                    String authenticatorData, String signature) {
    }

    public record SignFinishResponse(String signedDocument, String format, String level, List<String> steps) {
    }

    @PostMapping("/sign/finish")
    public SignFinishResponse signFinish(@RequestBody SignFinishRequest req) {
        Pending p = pending.remove(req.ticket());
        if (p == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않거나 만료된 서명 세션");
        }
        authenticateService();
        try {
            // 1) WebAuthn 어서션 + PIN → SAM 이 SAD 발급
            AuthorizeResponse auth = rssp.authorizeFinish(new AuthorizeFinishRequest(
                    p.credentialId(), List.of(p.hashB64()), signerId, req.pin(),
                    req.webauthnCredId(), req.clientDataJSON(), req.authenticatorData(), req.signature()));

            // 2) SAD 로 서명값 요청
            SignHashResponse sign = rssp.signHash(new SignHashRequest(
                    p.credentialId(), List.of(p.hashB64()), Oid.SHA256, Oid.SHA256_WITH_RSA, auth.sad()));
            if (sign.signatures() == null || sign.signatures().isEmpty()) {
                throw new RemoteSignException("RSSP 가 서명값을 반환하지 않았습니다");
            }
            byte[] signatureValue = Base64.getDecoder().decode(sign.signatures().get(0));

            // 3) 코어로 KR-AdES 서명객체 패키징
            SignResult result = coordinator.assemble(p.dts().core(), signatureValue, p.signingCertificate());

            List<String> steps = List.of(
                    "① 원문 다이제스트 계산(로컬, SHA-256)",
                    "② WebAuthn 사용자 인증 완료 → SAM 이 SAD 발급(해시 바인딩)",
                    "③ SAD 로 signHash → SAM 검증 → HSM 서명연산",
                    "④ KR-AdES 서명객체 패키징 완료");
            return new SignFinishResponse(
                    new String(result.signedDocument(), StandardCharsets.UTF_8),
                    result.format().profileName(), result.level().code(), steps);
        } catch (RemoteSignException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "원격서명 실패: " + e.getMessage());
        }
    }

    // === 서명: TOTP 대체 경로(WebAuthn 미지원 환경) ===

    public record SignTotpRequest(String text, String format, String level, String packaging, String pin, String otp) {
    }

    @PostMapping("/sign/totp")
    public SignFinishResponse signTotp(@RequestBody SignTotpRequest req) {
        if (req.text() == null || req.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "원문(text)이 비어 있습니다");
        }
        authenticateService();
        KrAdesFormat format = parse(KrAdesFormat.class, req.format(), KrAdesFormat.MADES);
        KrAdesLevel level = parse(KrAdesLevel.class, req.level(), KrAdesLevel.KR_B);
        PackagingType packaging = parse(PackagingType.class, req.packaging(), PackagingType.ENVELOPING);
        byte[] document = req.text().getBytes(StandardCharsets.UTF_8);

        CredentialInfo cred = rssp.credentialInfo(defaultCredentialId);
        byte[] cert = Base64.getDecoder().decode(cred.certChainB64().get(0));
        DataToSign dts = coordinator.prepare(new SignRequest(document, format, level, packaging));
        String hashB64 = Base64.getEncoder().encodeToString(dts.digest());

        try {
            AuthorizeResponse auth = rssp.authorize(
                    new com.electcerti.krdss.dss.remote.CscModels.AuthorizeRequest(
                            defaultCredentialId, List.of(hashB64), signerId, req.pin(), req.otp()));
            SignHashResponse sign = rssp.signHash(new SignHashRequest(
                    defaultCredentialId, List.of(hashB64), Oid.SHA256, Oid.SHA256_WITH_RSA, auth.sad()));
            byte[] signatureValue = Base64.getDecoder().decode(sign.signatures().get(0));
            SignResult result = coordinator.assemble(dts.core(), signatureValue, cert);
            return new SignFinishResponse(
                    new String(result.signedDocument(), StandardCharsets.UTF_8),
                    result.format().profileName(), result.level().code(),
                    List.of("PIN+TOTP 인증 → SAD 발급 → signHash → 서명객체 패키징"));
        } catch (RemoteSignException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "원격서명 실패: " + e.getMessage());
        }
    }

    // === 서명 검증 ===

    public record VerifyRequest(String signedDocument, String originalText) {
    }

    @PostMapping("/verify")
    public RemoteSignVerifier.Report verify(@RequestBody VerifyRequest req) {
        if (req.signedDocument() == null || req.signedDocument().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "서명객체가 비어 있습니다");
        }
        byte[] signed = req.signedDocument().getBytes(StandardCharsets.UTF_8);
        byte[] original = (req.originalText() == null || req.originalText().isBlank())
                ? null : req.originalText().getBytes(StandardCharsets.UTF_8);
        return verifier.verify(signed, original);
    }

    // === helpers ===

    private String toPem(String certB64) {
        StringBuilder sb = new StringBuilder("-----BEGIN CERTIFICATE-----\n");
        for (int i = 0; i < certB64.length(); i += 64) {
            sb.append(certB64, i, Math.min(i + 64, certB64.length())).append('\n');
        }
        return sb.append("-----END CERTIFICATE-----\n").toString();
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String v = value.trim().toUpperCase().replace('-', '_');
        // 웹 표기 → KR 레벨 매핑 (B_B→KR_B 등)
        if (type == KrAdesLevel.class) {
            v = switch (v) {
                case "B_B", "BB" -> "KR_B";
                case "B_T", "BT" -> "KR_T";
                case "B_LT", "BLT" -> "KR_LT";
                case "B_LTA", "BLTA" -> "KR_LTA";
                default -> v;
            };
        }
        try {
            return Enum.valueOf(type, v);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
