package com.electcerti.krdss.dss.remote;

import com.electcerti.krdss.dss.remote.CscModels.AuthorizeBeginRequest;
import com.electcerti.krdss.dss.remote.CscModels.AuthorizeBeginResponse;
import com.electcerti.krdss.dss.remote.CscModels.AuthorizeFinishRequest;
import com.electcerti.krdss.dss.remote.CscModels.AuthorizeRequest;
import com.electcerti.krdss.dss.remote.CscModels.AuthorizeResponse;
import com.electcerti.krdss.dss.remote.CscModels.CredentialInfo;
import com.electcerti.krdss.dss.remote.CscModels.PasskeyRegisterRequest;
import com.electcerti.krdss.dss.remote.CscModels.PasskeyRegisterResponse;
import com.electcerti.krdss.dss.remote.CscModels.CredentialsListResponse;
import com.electcerti.krdss.dss.remote.CscModels.SignHashRequest;
import com.electcerti.krdss.dss.remote.CscModels.SignHashResponse;
import com.electcerti.krdss.dss.remote.CscModels.TokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * RSSP(원격서명 서비스 제공자)의 CSC v2 API 를 호출하는 클라이언트.
 *
 * <p>이용사 측 서명요소(SIC)가 사용한다. CSC 2계층 인증을 지원한다:</p>
 * <ol>
 *   <li><b>서비스 인증</b> — {@link #obtainAccessToken}로 OAuth2 client_credentials
 *       액세스 토큰을 받아 이후 CSC 호출에 Bearer 로 첨부</li>
 *   <li><b>서명자 인증·인가</b> — {@link #authorize}로 PIN+TOTP 를 제출해 SAD 토큰 수령</li>
 *   <li><b>서명</b> — {@link #signHash}로 다이제스트 + SAD 전송 → 서명값 수령</li>
 * </ol>
 *
 * <p>JDK 내장 {@link HttpClient} 와 Jackson 만 사용해 외부 의존을 최소화한다.</p>
 */
public class RemoteSignClient {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();

    /** 서비스 인증 액세스 토큰(Bearer). {@link #obtainAccessToken} 호출 후 설정된다. */
    private volatile String accessToken;

    public RemoteSignClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** OAuth2 client_credentials 로 액세스 토큰을 발급받아 보관한다(서비스 인증). */
    public TokenResponse obtainAccessToken(String clientId, String clientSecret) {
        String body = "{\"grant_type\":\"client_credentials\",\"client_id\":\"" + clientId
                + "\",\"client_secret\":\"" + clientSecret + "\"}";
        TokenResponse token = post("/oauth2/token", body, TokenResponse.class, false);
        this.accessToken = token.accessToken();
        return token;
    }

    /** 서명자 인증(PIN+TOTP)으로 SAD 토큰을 발급받는다. (CSC {@code credentials/authorize}) */
    public AuthorizeResponse authorize(AuthorizeRequest request) {
        return post("/csc/v2/credentials/authorize", toJson(request), AuthorizeResponse.class, true);
    }

    /** 서명자 패스키(WebAuthn 자격증명)를 등록한다. */
    public PasskeyRegisterResponse registerPasskey(PasskeyRegisterRequest request) {
        return post("/csc/v2/passkey/register", toJson(request), PasskeyRegisterResponse.class, true);
    }

    /** WebAuthn 인증 챌린지를 발급받는다. (CSC {@code credentials/authorize/begin}) */
    public AuthorizeBeginResponse authorizeBegin(AuthorizeBeginRequest request) {
        return post("/csc/v2/credentials/authorize/begin", toJson(request), AuthorizeBeginResponse.class, true);
    }

    /** WebAuthn 어서션 + PIN 을 제출해 SAD 토큰을 발급받는다. (CSC {@code credentials/authorize/finish}) */
    public AuthorizeResponse authorizeFinish(AuthorizeFinishRequest request) {
        return post("/csc/v2/credentials/authorize/finish", toJson(request), AuthorizeResponse.class, true);
    }

    /** 서명자가 사용할 수 있는 자격증명 목록을 조회한다. (CSC {@code credentials/list}) */
    public CredentialsListResponse listCredentials() {
        return post("/csc/v2/credentials/list", "{}", CredentialsListResponse.class, true);
    }

    /** 특정 자격증명의 인증서·키 정보를 조회한다. (CSC {@code credentials/info}) */
    public CredentialInfo credentialInfo(String credentialID) {
        String body = "{\"credentialID\":\"" + credentialID + "\"}";
        return post("/csc/v2/credentials/info", body, CredentialInfo.class, true);
    }

    /** 다이제스트 + SAD 를 전송해 서명값을 발급받는다. (CSC {@code signatures/signHash}) */
    public SignHashResponse signHash(SignHashRequest request) {
        return post("/csc/v2/signatures/signHash", toJson(request), SignHashResponse.class, true);
    }

    private <T> T post(String path, String json, Class<T> type, boolean authenticated) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            if (authenticated) {
                if (accessToken == null) {
                    throw new RemoteSignException("서비스 인증 토큰이 없습니다 — obtainAccessToken() 선행 필요");
                }
                builder.header("Authorization", "Bearer " + accessToken);
            }
            HttpResponse<String> res = http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() / 100 != 2) {
                throw new RemoteSignException("RSSP 오류 응답 " + res.statusCode() + ": " + res.body());
            }
            return mapper.readValue(res.body(), type);
        } catch (RemoteSignException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteSignException("RSSP 호출 실패: " + baseUrl + path, e);
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RemoteSignException("요청 직렬화 실패", e);
        }
    }
}
