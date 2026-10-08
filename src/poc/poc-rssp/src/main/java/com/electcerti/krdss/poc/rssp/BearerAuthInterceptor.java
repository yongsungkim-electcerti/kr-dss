package com.electcerti.krdss.poc.rssp;

import com.electcerti.krdss.dss.remote.RemoteSignException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * CSC v2 API 의 서비스 인증 게이트.
 *
 * <p>{@code /csc/v2/**} 요청에 유효한 OAuth2 Bearer 액세스 토큰을 요구한다.
 * 토큰이 없거나 서명·만료 검증에 실패하면 401 로 차단한다.</p>
 */
@Component
public class BearerAuthInterceptor implements HandlerInterceptor {

    private final AccessTokenService tokens;

    public BearerAuthInterceptor(AccessTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer 액세스 토큰 필요");
        }
        try {
            String clientId = tokens.verify(header.substring("Bearer ".length()).trim());
            request.setAttribute("clientId", clientId);
            return true;
        } catch (RemoteSignException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰: " + e.getMessage());
        }
    }
}
