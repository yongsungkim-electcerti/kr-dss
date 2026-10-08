package com.electcerti.krdss.poc.sam;

import com.electcerti.krdss.dss.remote.Jws;
import com.electcerti.krdss.dss.remote.SignatureActivationData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * SAD 토큰 발급·검증 코덱.
 *
 * <p>SAM 만 보유하는 비밀키로 SAD 페이로드를 HMAC 서명(JWS HS256)하여 위변조를
 * 탐지할 수 있는 토큰으로 만든다. 발급도 검증도 SAM 내부에서만 이뤄지므로,
 * 외부(SIC/RSSP)는 SAD 를 위조하거나 내용을 바꿀 수 없다.</p>
 */
@Component
public class SadCodec {

    private final byte[] secret;
    private final ObjectMapper mapper = new ObjectMapper();

    public SadCodec(@Value("${krdss.sam.sad-secret:sam-sad-signing-secret-change-me}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** SAD 페이로드를 서명된 토큰으로 발급한다. */
    public String issue(SignatureActivationData sad) {
        try {
            return Jws.sign(mapper.writeValueAsString(sad), secret);
        } catch (Exception e) {
            throw new IllegalStateException("SAD 발급 실패", e);
        }
    }

    /** 토큰 서명을 검증하고 SAD 페이로드를 복원한다. 서명 불일치 시 예외. */
    public SignatureActivationData verify(String token) {
        try {
            String payload = Jws.verify(token, secret);
            return mapper.readValue(payload, SignatureActivationData.class);
        } catch (com.electcerti.krdss.dss.remote.RemoteSignException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("SAD 파싱 실패", e);
        }
    }
}
