package com.electcerti.krdss.poc.rp.local;

import com.electcerti.krdss.dss.core.verify.VerificationResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * 특허-A Mode 1 (WebAuthn 로컬 서명) 데모 API — {@code /api/local/*}.
 *
 * <p>기존 Mode 2(원격서명, {@code /api/*})와 분리된 경로. 브라우저가
 * {@code navigator.credentials.create()/get()} 으로 패스키를 만들고, 결속 challenge 에
 * 대한 어서션을 그대로 전자서명으로 사용한다.</p>
 */
@RestController
@RequestMapping("/api/local")
public class Mode1WebAuthnController {

    private final Mode1LocalSignService service;

    public Mode1WebAuthnController(Mode1LocalSignService service) {
        this.service = service;
    }

    // === 등록 ===

    public record RegisterRequest(String publicKey, String credentialId, Integer coseAlg, String aaguid) {
    }

    @PostMapping("/register")
    public Mode1LocalSignService.RegisterResult register(@RequestBody RegisterRequest req) {
        if (req.publicKey() == null || req.credentialId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "publicKey/credentialId 필요");
        }
        try {
            byte[] spki = Base64.getUrlDecoder().decode(req.publicKey());
            byte[] aaguid = req.aaguid() == null ? null : Base64.getUrlDecoder().decode(req.aaguid());
            return service.register(spki, req.credentialId(), req.coseAlg() == null ? 0 : req.coseAlg(), aaguid);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/certificate/{credentialId}")
    public Mode1LocalSignService.CertificateLookupResult certificate(
            @PathVariable String credentialId) {
        try {
            return service.certificate(credentialId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @GetMapping("/certificates")
    public List<Mode1LocalSignService.CertificateSummary> certificates() {
        return service.certificates();
    }

    // === 서명 begin ===

    public record BeginRequest(String text, String credentialId) {
    }

    @PostMapping("/sign/begin")
    public Mode1LocalSignService.BeginResult begin(@RequestBody BeginRequest req) {
        if (req.text() == null || req.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "원문(text)이 비어 있습니다");
        }
        try {
            return service.begin(req.text().getBytes(StandardCharsets.UTF_8), req.credentialId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    // === 서명 finish ===

    public record FinishRequest(String ticket, String webauthnCredId,
                                String clientDataJSON, String authenticatorData, String signature) {
    }

    @PostMapping("/sign/finish")
    public FinishResponse finish(@RequestBody FinishRequest req) {
        try {
            Mode1LocalSignService.FinishResult r = service.finish(
                    req.ticket(), req.webauthnCredId(),
                    Base64.getUrlDecoder().decode(req.clientDataJSON()),
                    Base64.getUrlDecoder().decode(req.authenticatorData()),
                    Base64.getUrlDecoder().decode(req.signature()));
            return new FinishResponse(r.containerB64(), r.report(), r.signerCertificate());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    public record FinishResponse(String signedContainer, VerificationResult report,
                                 Mode1LocalSignService.CertificateDetail signerCertificate) {
    }

    // === 검증 ===

    public record VerifyRequest(String signedContainer, String originalText) {
    }

    @PostMapping("/verify")
    public VerifyResponse verify(@RequestBody VerifyRequest req) {
        if (req.signedContainer() == null || req.signedContainer().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "signedContainer 가 비어 있습니다");
        }
        try {
            byte[] container = Base64.getDecoder().decode(req.signedContainer());
            byte[] original = (req.originalText() == null || req.originalText().isBlank())
                    ? null : req.originalText().getBytes(StandardCharsets.UTF_8);
            return new VerifyResponse(service.verify(container, original),
                    service.signerCertificate(container));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    public record VerifyResponse(VerificationResult report,
                                 Mode1LocalSignService.CertificateDetail signerCertificate) {
    }
}
