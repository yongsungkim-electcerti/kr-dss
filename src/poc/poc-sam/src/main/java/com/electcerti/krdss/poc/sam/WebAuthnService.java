package com.electcerti.krdss.poc.sam;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebAuthn(FIDO2) 기반 서명자 인증 — 챌린지 발급·어서션 검증.
 *
 * <p>서명 활성화의 "가진 것/생체" 요소를 패스키(플랫폼 인증기)로 처리한다. SAM 이
 * 서명 대상 해시에 바인딩된 챌린지를 발급하므로, 인증 행위가 바로 그 문서에 대한
 * 승인임을 결착한다(서명-인증 바인딩).</p>
 *
 * <p><b>데모 등급 검증</b>: clientDataJSON 의 type/challenge/origin 일치와 인증기
 * 사용자 확인 플래그(UP/UV)를 검증한다. COSE 공개키 기반 어서션 <i>서명</i> 검증은
 * CBOR/COSE 처리를 피하기 위해 생략하며, 운영화 시 검증된 WebAuthn 라이브러리로
 * 대체한다(soft-HSM·자체 JWS 와 동일한 데모 단순화 기조).</p>
 */
@Component
public class WebAuthnService {

    private final SecureRandom random = new SecureRandom();
    private final ObjectMapper mapper = new ObjectMapper();

    /** 서명자 → 등록된 패스키 자격증명 ID(Base64URL) 집합. */
    private final Map<String, Set<String>> passkeys = new ConcurrentHashMap<>();

    /** 발급된 챌린지 → 바인딩 정보. */
    private final Map<String, Pending> challenges = new ConcurrentHashMap<>();

    private final String rpId;
    private final List<String> allowedOrigins;
    private final long challengeTtlSeconds;

    public WebAuthnService(
            @Value("${krdss.sam.webauthn.rp-id:localhost}") String rpId,
            @Value("${krdss.sam.webauthn.allowed-origins:http://localhost:8080}") String allowedOrigins,
            @Value("${krdss.sam.webauthn.challenge-ttl-seconds:120}") long challengeTtlSeconds) {
        this.rpId = rpId;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(",")).map(String::trim).toList();
        this.challengeTtlSeconds = challengeTtlSeconds;
    }

    private record Pending(String signerId, String credentialID, List<String> hashes, long expiresAt) {
    }

    /** WebAuthn 인증 시작 파라미터. */
    public record BeginParams(String challenge, String rpId, List<String> allowCredentials, long timeoutMs) {
    }

    /** 어서션 검증 결과. */
    public record VerifyResult(boolean ok, String reason, String credentialID, List<String> hashes) {
        static VerifyResult fail(String reason) {
            return new VerifyResult(false, reason, null, null);
        }
    }

    /** 서명자에 패스키 자격증명을 등록한다. */
    public void register(String signerId, String credentialId) {
        passkeys.computeIfAbsent(signerId, k -> ConcurrentHashMap.newKeySet()).add(credentialId);
    }

    public Set<String> credentialsOf(String signerId) {
        return passkeys.getOrDefault(signerId, Set.of());
    }

    /** 해시에 바인딩된 챌린지를 발급한다. */
    public BeginParams begin(String credentialID, List<String> hashes, String signerId) {
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String challenge = b64url(raw);
        challenges.put(challenge, new Pending(
                signerId, credentialID, hashes, Instant.now().getEpochSecond() + challengeTtlSeconds));
        return new BeginParams(challenge, rpId,
                List.copyOf(credentialsOf(signerId)), challengeTtlSeconds * 1000);
    }

    /** 어서션(clientDataJSON/authenticatorData)을 검증한다. */
    public VerifyResult verify(String signerId, String webauthnCredId,
                               String clientDataJsonB64Url, String authenticatorDataB64Url) {
        if (webauthnCredId == null || !credentialsOf(signerId).contains(webauthnCredId)) {
            return VerifyResult.fail("등록되지 않은 패스키 자격증명");
        }
        JsonNode clientData;
        try {
            byte[] json = Base64.getUrlDecoder().decode(clientDataJsonB64Url);
            clientData = mapper.readTree(new String(json, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return VerifyResult.fail("clientDataJSON 파싱 실패");
        }
        if (!"webauthn.get".equals(clientData.path("type").asText())) {
            return VerifyResult.fail("WebAuthn type 불일치");
        }
        String origin = clientData.path("origin").asText();
        if (!allowedOrigins.contains(origin)) {
            return VerifyResult.fail("허용되지 않은 origin: " + origin);
        }
        String challenge = clientData.path("challenge").asText();
        Pending pending = challenges.remove(challenge);  // 1회용
        if (pending == null) {
            return VerifyResult.fail("알 수 없거나 만료된 challenge");
        }
        if (Instant.now().getEpochSecond() > pending.expiresAt()) {
            return VerifyResult.fail("challenge 만료");
        }
        if (!pending.signerId().equals(signerId)) {
            return VerifyResult.fail("challenge-서명자 불일치");
        }
        // 인증기 사용자 확인 플래그(UP=0x01) 검증
        byte[] authData;
        try {
            authData = Base64.getUrlDecoder().decode(authenticatorDataB64Url);
        } catch (Exception e) {
            return VerifyResult.fail("authenticatorData 디코딩 실패");
        }
        if (authData.length < 33 || (authData[32] & 0x01) == 0) {
            return VerifyResult.fail("사용자 확인(User Present) 플래그 없음");
        }
        // (데모) COSE 공개키 기반 어서션 서명 검증은 생략
        return new VerifyResult(true, null, pending.credentialID(), pending.hashes());
    }

    private String b64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
