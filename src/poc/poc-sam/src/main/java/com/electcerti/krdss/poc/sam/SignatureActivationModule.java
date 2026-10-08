package com.electcerti.krdss.poc.sam;

import com.electcerti.krdss.dss.remote.SignatureActivationData;
import com.electcerti.krdss.dss.remote.Totp;
import com.electcerti.krdss.poc.sam.SignerRegistry.SignerAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 서명활성화모듈의 핵심 로직 — 서명자 인증과 SAD 수명주기.
 *
 * <p>SAM 은 두 책임을 갖는다:</p>
 * <ol>
 *   <li><b>인증·발급</b>({@link #authorize}) — 서명자 2요소 인증(PIN+TOTP)을 검증하고,
 *       성공 시 서명 대상 해시에 바인딩된 SAD 토큰을 발급한다. SAD 는 SAM 만이
 *       발급하므로 단독 통제권의 출처가 SAM 에 집중된다.</li>
 *   <li><b>검증</b>({@link #validateSad}) — 서명 시점에 제시된 SAD 토큰의 서명·바인딩·
 *       만료·재사용을 검증한다. 통과해야만 HSM 서명키가 활성화된다.</li>
 * </ol>
 */
@Component
public class SignatureActivationModule {

    private final SignerRegistry registry;
    private final SadCodec sadCodec;
    private final WebAuthnService webAuthn;

    /** 발급된 SAD 유효시간(초). */
    private final long sadTtlSeconds;

    /** 사용된 nonce — 재사용(replay) 방지. */
    private final Set<String> usedNonces = ConcurrentHashMap.newKeySet();

    public SignatureActivationModule(
            SignerRegistry registry,
            SadCodec sadCodec,
            WebAuthnService webAuthn,
            @Value("${krdss.sam.sad-ttl-seconds:300}") long sadTtlSeconds) {
        this.registry = registry;
        this.sadCodec = sadCodec;
        this.webAuthn = webAuthn;
        this.sadTtlSeconds = sadTtlSeconds;
    }

    /** 인증·발급 결과. */
    public record AuthResult(boolean ok, String reason, String sadToken, long expiresInSec) {
        static AuthResult fail(String reason) {
            return new AuthResult(false, reason, null, 0);
        }
    }

    /** 검증 결과. */
    public record Result(boolean valid, String reason) {
        static Result ok() {
            return new Result(true, null);
        }

        static Result fail(String reason) {
            return new Result(false, reason);
        }
    }

    /**
     * 서명자 2요소 인증을 검증하고 성공 시 SAD 토큰을 발급한다.
     *
     * @param credentialID 자격증명
     * @param hashes       서명 대상 다이제스트(Base64) — SAD 에 바인딩
     * @param signerId     서명자 식별자
     * @param pin          서명자 PIN(아는 것)
     * @param otp          TOTP 코드(가진 것)
     */
    public AuthResult authorize(String credentialID, List<String> hashes,
                                String signerId, String pin, String otp) {
        if (hashes == null || hashes.isEmpty()) {
            return AuthResult.fail("서명 대상 해시 없음");
        }
        SignerAccount account = registry.find(signerId);
        if (account == null) {
            return AuthResult.fail("알 수 없는 서명자");
        }
        // 1요소: 아는 것(PIN)
        if (!registry.verifyPin(account, pin)) {
            return AuthResult.fail("PIN 인증 실패");
        }
        // 2요소: 가진 것(TOTP)
        if (!Totp.verify(account.totpSecretBase32(), otp, Instant.now().getEpochSecond())) {
            return AuthResult.fail("OTP 인증 실패");
        }

        return issueSad(credentialID, hashes, signerId);
    }

    /** 서명자에 패스키 자격증명을 등록한다. */
    public void registerPasskey(String signerId, String credentialId) {
        webAuthn.register(signerId, credentialId);
    }

    /**
     * WebAuthn 인증 시작 — 해시에 바인딩된 챌린지를 발급한다.
     */
    public WebAuthnService.BeginParams authorizeBegin(String credentialID, List<String> hashes, String signerId) {
        return webAuthn.begin(credentialID, hashes, signerId);
    }

    /**
     * WebAuthn 어서션 + PIN 으로 2요소 인증을 완성하고 SAD 토큰을 발급한다.
     *
     * <p>인증 통과 시, 챌린지에 바인딩되어 있던 자격증명·해시로 SAD 를 발급한다.</p>
     */
    public AuthResult authorizeFinish(String signerId, String pin, String webauthnCredId,
                                      String clientDataJSON, String authenticatorData) {
        SignerAccount account = registry.find(signerId);
        if (account == null) {
            return AuthResult.fail("알 수 없는 서명자");
        }
        // 1요소: 아는 것(PIN)
        if (!registry.verifyPin(account, pin)) {
            return AuthResult.fail("PIN 인증 실패");
        }
        // 2요소: 가진 것/생체(WebAuthn 어서션)
        WebAuthnService.VerifyResult webAuthnResult =
                webAuthn.verify(signerId, webauthnCredId, clientDataJSON, authenticatorData);
        if (!webAuthnResult.ok()) {
            return AuthResult.fail("WebAuthn 인증 실패: " + webAuthnResult.reason());
        }
        // 챌린지에 바인딩된 자격증명·해시로 SAD 발급(서명-인증 결착)
        return issueSad(webAuthnResult.credentialID(), webAuthnResult.hashes(), signerId);
    }

    private AuthResult issueSad(String credentialID, List<String> hashes, String signerId) {
        long now = Instant.now().getEpochSecond();
        SignatureActivationData sad = new SignatureActivationData(
                credentialID, hashes, signerId, UUID.randomUUID().toString(), now, now + sadTtlSeconds);
        return new AuthResult(true, null, sadCodec.issue(sad), sadTtlSeconds);
    }

    /**
     * 제시된 SAD 토큰이 서명을 활성화할 수 있는지 검증한다.
     *
     * @param sadToken        SAM 이 발급한 SAD 토큰
     * @param credentialID    서명 요청의 자격증명
     * @param requestedHashes 서명 대상 다이제스트(Base64) 목록
     */
    public Result validateSad(String sadToken, String credentialID, List<String> requestedHashes) {
        if (sadToken == null || sadToken.isBlank()) {
            return Result.fail("SAD 누락");
        }
        SignatureActivationData sad;
        try {
            sad = sadCodec.verify(sadToken);  // 서명 검증(위변조 탐지)
        } catch (RuntimeException e) {
            return Result.fail("SAD 서명 검증 실패: " + e.getMessage());
        }
        if (!credentialID.equals(sad.credentialID())) {
            return Result.fail("자격증명 불일치");
        }
        if (sad.hashes() == null || !sad.hashes().containsAll(requestedHashes)) {
            return Result.fail("해시 바인딩 불일치 — SAD 가 승인하지 않은 다이제스트");
        }
        long now = Instant.now().getEpochSecond();
        if (now > sad.expiresAtEpochSec()) {
            return Result.fail("SAD 만료");
        }
        if (sad.nonce() == null || !usedNonces.add(sad.nonce())) {
            return Result.fail("nonce 재사용 감지(replay)");
        }
        return Result.ok();
    }
}
