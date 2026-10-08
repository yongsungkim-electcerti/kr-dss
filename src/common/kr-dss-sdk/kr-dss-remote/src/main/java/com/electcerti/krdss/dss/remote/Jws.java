package com.electcerti.krdss.dss.remote;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 최소 구현 JWS(HS256) 컴팩트 토큰 코덱.
 *
 * <p>OAuth2 액세스 토큰(서비스 인증)과 서명활성화데이터(SAD) 토큰을 HMAC-SHA256 으로
 * 서명·검증하는 데 공통으로 사용한다. 외부 JWT 라이브러리 의존을 피하기 위해
 * {@code base64url(header).base64url(payload).base64url(HMAC)} 형식만 직접 구현한다.</p>
 *
 * <p>운영에서는 검증된 JWT 라이브러리(서명·클레임 검증·키 회전 포함) 사용을 권장한다.</p>
 */
public final class Jws {

    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DEC = Base64.getUrlDecoder();

    /** {"alg":"HS256","typ":"JWT"} 고정 헤더. */
    private static final String HEADER_B64 =
            B64URL.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private Jws() {
    }

    /** 페이로드(JSON 문자열)를 HS256 으로 서명한 컴팩트 토큰을 만든다. */
    public static String sign(String payloadJson, byte[] secret) {
        String body = HEADER_B64 + "." + B64URL.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        String sig = B64URL.encodeToString(hmac(body.getBytes(StandardCharsets.UTF_8), secret));
        return body + "." + sig;
    }

    /**
     * 토큰 서명을 검증하고 페이로드(JSON 문자열)를 반환한다.
     *
     * @throws RemoteSignException 형식 오류 또는 서명 불일치
     */
    public static String verify(String token, byte[] secret) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new RemoteSignException("토큰 형식 오류");
        }
        String body = parts[0] + "." + parts[1];
        byte[] expected = hmac(body.getBytes(StandardCharsets.UTF_8), secret);
        byte[] actual;
        try {
            actual = B64URL_DEC.decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new RemoteSignException("토큰 서명 디코딩 실패");
        }
        if (!MessageDigest.isEqual(expected, actual)) {
            throw new RemoteSignException("토큰 서명 불일치(위변조 의심)");
        }
        return new String(B64URL_DEC.decode(parts[1]), StandardCharsets.UTF_8);
    }

    private static byte[] hmac(byte[] data, byte[] secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new RemoteSignException("HMAC 계산 실패", e);
        }
    }
}
