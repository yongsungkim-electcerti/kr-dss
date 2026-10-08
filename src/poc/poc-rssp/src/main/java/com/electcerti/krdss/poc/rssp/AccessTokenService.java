package com.electcerti.krdss.poc.rssp;

import com.electcerti.krdss.dss.remote.Jws;
import com.electcerti.krdss.dss.remote.RemoteSignException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

/**
 * OAuth2 서비스 인증용 액세스 토큰 발급·검증.
 *
 * <p>client_credentials 그랜트로 등록된 클라이언트(이용사 서비스)를 인증하고,
 * HMAC 서명된 Bearer 토큰을 발급한다. CSC API 호출 시 이 토큰의 서명·만료를 검증한다.</p>
 */
@Service
public class AccessTokenService {

    private final byte[] secret;
    private final String clientId;
    private final String clientSecret;
    private final long ttlSeconds;
    private final ObjectMapper mapper = new ObjectMapper();

    public AccessTokenService(
            @Value("${krdss.rssp.oauth.token-secret:rssp-oauth-token-secret-change-me}") String secret,
            @Value("${krdss.rssp.oauth.client-id:krdss-rp-client}") String clientId,
            @Value("${krdss.rssp.oauth.client-secret:rp-client-secret}") String clientSecret,
            @Value("${krdss.rssp.oauth.token-ttl-seconds:600}") long ttlSeconds) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.ttlSeconds = ttlSeconds;
    }

    public long ttlSeconds() {
        return ttlSeconds;
    }

    /** 클라이언트 자격증명을 검증하고 액세스 토큰을 발급한다. 실패 시 예외. */
    public String issue(String reqClientId, String reqClientSecret) {
        if (!clientId.equals(reqClientId) || !clientSecret.equals(reqClientSecret)) {
            throw new RemoteSignException("클라이언트 인증 실패");
        }
        try {
            long now = Instant.now().getEpochSecond();
            Map<String, Object> claims = Map.of("sub", reqClientId, "iat", now, "exp", now + ttlSeconds);
            return Jws.sign(mapper.writeValueAsString(claims), secret);
        } catch (Exception e) {
            throw new RemoteSignException("액세스 토큰 발급 실패", e);
        }
    }

    /** Bearer 토큰의 서명·만료를 검증하고 client_id(sub)를 반환한다. 실패 시 예외. */
    @SuppressWarnings("unchecked")
    public String verify(String token) {
        String payload = Jws.verify(token, secret);  // 서명 검증(위변조 탐지)
        try {
            Map<String, Object> claims = mapper.readValue(payload, Map.class);
            long exp = ((Number) claims.get("exp")).longValue();
            if (Instant.now().getEpochSecond() > exp) {
                throw new RemoteSignException("액세스 토큰 만료");
            }
            return String.valueOf(claims.get("sub"));
        } catch (RemoteSignException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteSignException("액세스 토큰 클레임 파싱 실패", e);
        }
    }
}
