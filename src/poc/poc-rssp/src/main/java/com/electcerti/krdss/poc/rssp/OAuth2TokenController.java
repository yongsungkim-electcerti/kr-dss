package com.electcerti.krdss.poc.rssp;

import com.electcerti.krdss.dss.remote.RemoteSignException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * OAuth2 토큰 엔드포인트(서비스 인증).
 *
 * <p>client_credentials 그랜트로 이용사 서비스를 인증하고 Bearer 액세스 토큰을 발급한다.
 * 데모 단순화를 위해 JSON 본문을 받으며, 인증서버는 RSSP 에 내장한다(운영에서는 분리 권장).</p>
 */
@RestController
public class OAuth2TokenController {

    private final AccessTokenService tokens;

    public OAuth2TokenController(AccessTokenService tokens) {
        this.tokens = tokens;
    }

    public record TokenRequest(String grant_type, String client_id, String client_secret) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresInSec) {
    }

    @PostMapping("/oauth2/token")
    public TokenResponse token(@RequestBody TokenRequest req) {
        if (!"client_credentials".equals(req.grant_type())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 grant_type");
        }
        try {
            String accessToken = tokens.issue(req.client_id(), req.client_secret());
            return new TokenResponse(accessToken, "Bearer", tokens.ttlSeconds());
        } catch (RemoteSignException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }
}
