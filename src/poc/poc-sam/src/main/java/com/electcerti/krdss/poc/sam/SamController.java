package com.electcerti.krdss.poc.sam;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * SAM 서명활성화 API.
 *
 * <p>RSSP 로부터 서명요청 + SAD 를 받아 {@link SignatureActivationModule}로 단독
 * 통제권을 검증한 뒤, 검증을 통과한 경우에만 HSM 에 활성화 토큰을 제시하여
 * 서명연산을 지시한다. SAM 이 HSM 의 게이트키퍼 역할을 한다.</p>
 */
@RestController
@RequestMapping("/sam")
public class SamController {

    private final SignatureActivationModule sam;
    private final RestClient hsm;
    private final String hsmActivationToken;

    public SamController(SignatureActivationModule sam,
                         @Value("${krdss.sam.hsm-base-url:http://localhost:8092}") String hsmBaseUrl,
                         @Value("${krdss.sam.hsm-activation-token:SAM-INTERNAL-TOKEN}") String hsmActivationToken) {
        this.sam = sam;
        this.hsm = RestClient.create(hsmBaseUrl);
        this.hsmActivationToken = hsmActivationToken;
    }

    /**
     * 서명자 2요소 인증 → SAD 발급 요청.
     *
     * @param credentialID 자격증명 식별자
     * @param hashes       서명 대상 다이제스트(Base64) 목록
     * @param signerId     서명자 식별자
     * @param pin          서명자 PIN
     * @param otp          TOTP 코드
     */
    public record AuthorizeRequest(
            String credentialID, List<String> hashes, String signerId, String pin, String otp) {
    }

    public record AuthorizeResponse(String sad, long expiresInSec) {
    }

    /**
     * @param credentialID 자격증명 식별자
     * @param keyAlias     HSM 키 별칭
     * @param hashes       서명 대상 다이제스트(Base64) 목록
     * @param sad          SAM 이 발급한 SAD 토큰
     */
    public record ActivateRequest(String credentialID, String keyAlias, List<String> hashes, String sad) {
    }

    public record ActivateResponse(List<String> signatures) {
    }

    private record HsmSignRequest(String keyAlias, String digestB64, String activationToken) {
    }

    private record HsmSignResponse(String signatureB64) {
    }

    /** 서명자 2요소 인증을 검증하고 SAD 토큰을 발급한다. */
    @PostMapping("/authorize")
    public AuthorizeResponse authorize(@RequestBody AuthorizeRequest req) {
        SignatureActivationModule.AuthResult result =
                sam.authorize(req.credentialID(), req.hashes(), req.signerId(), req.pin(), req.otp());
        if (!result.ok()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SAM 서명자 인증 실패: " + result.reason());
        }
        return new AuthorizeResponse(result.sadToken(), result.expiresInSec());
    }

    // --- WebAuthn 기반 서명자 인증 ---

    public record PasskeyRegisterRequest(String signerId, String credentialId, String clientDataJSON) {
    }

    public record PasskeyRegisterResponse(boolean registered, String message) {
    }

    public record BeginRequest(String credentialID, List<String> hashes, String signerId) {
    }

    public record FinishRequest(
            String credentialID, List<String> hashes, String signerId, String pin,
            String webauthnCredId, String clientDataJSON, String authenticatorData, String signature) {
    }

    /** 서명자 패스키를 등록한다(데모: attestation 검증 생략). */
    @PostMapping("/passkey/register")
    public PasskeyRegisterResponse registerPasskey(@RequestBody PasskeyRegisterRequest req) {
        if (req.signerId() == null || req.credentialId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "signerId/credentialId 필요");
        }
        sam.registerPasskey(req.signerId(), req.credentialId());
        return new PasskeyRegisterResponse(true, "패스키 등록 완료");
    }

    /** WebAuthn 인증 챌린지를 발급한다(해시 바인딩). */
    @PostMapping("/authorize/begin")
    public WebAuthnService.BeginParams authorizeBegin(@RequestBody BeginRequest req) {
        return sam.authorizeBegin(req.credentialID(), req.hashes(), req.signerId());
    }

    /** WebAuthn 어서션 + PIN 을 검증하고 SAD 토큰을 발급한다. */
    @PostMapping("/authorize/finish")
    public AuthorizeResponse authorizeFinish(@RequestBody FinishRequest req) {
        SignatureActivationModule.AuthResult result = sam.authorizeFinish(
                req.signerId(), req.pin(), req.webauthnCredId(), req.clientDataJSON(), req.authenticatorData());
        if (!result.ok()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SAM 서명자 인증 실패: " + result.reason());
        }
        return new AuthorizeResponse(result.sadToken(), result.expiresInSec());
    }

    @PostMapping("/signatures/signHash")
    public ActivateResponse signHash(@RequestBody ActivateRequest req) {
        // 1) SAD 토큰 검증 — 단독 통제권 (sole control)
        SignatureActivationModule.Result result = sam.validateSad(req.sad(), req.credentialID(), req.hashes());
        if (!result.valid()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SAM 서명활성화 거부: " + result.reason());
        }

        // 2) 검증 통과 → HSM 에 활성화 토큰을 제시하여 서명 지시
        List<String> signatures = req.hashes().stream()
                .map(hash -> callHsm(req.keyAlias(), hash))
                .toList();
        return new ActivateResponse(signatures);
    }

    private String callHsm(String keyAlias, String digestB64) {
        try {
            HsmSignResponse res = hsm.post()
                    .uri("/hsm/sign")
                    .body(new HsmSignRequest(keyAlias, digestB64, hsmActivationToken))
                    .retrieve()
                    .body(HsmSignResponse.class);
            if (res == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "HSM 빈 응답");
            }
            return res.signatureB64();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "HSM 호출 실패: " + e.getMessage());
        }
    }
}
