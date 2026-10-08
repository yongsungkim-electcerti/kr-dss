package com.electcerti.krdss.poc.hsm;

import com.electcerti.krdss.poc.hsm.SoftHsm.KeyEntry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * HSM 서명연산 API.
 *
 * <p>키 보관·서명연산만 노출한다. 서명요청은 SAM 이 발급한 활성화 토큰을
 * 동반해야 하며(데모에서는 공유 비밀로 모사), 토큰이 없으면 서명을 거부한다.
 * 이는 HSM 이 SAM 의 활성화 지시 없이는 서명키를 사용하지 않는 구조를 나타낸다.</p>
 */
@RestController
@RequestMapping("/hsm")
public class HsmController {

    private final SoftHsm hsm;

    /** SAM 만 알고 있는 활성화 토큰(데모용 공유 비밀). 운영에서는 SAM↔HSM 내부 채널/봉인. */
    private final String expectedActivationToken;

    public HsmController(SoftHsm hsm,
                         @Value("${krdss.hsm.activation-token:SAM-INTERNAL-TOKEN}") String expectedActivationToken) {
        this.hsm = hsm;
        this.expectedActivationToken = expectedActivationToken;
    }

    public record KeyView(String alias, String keyAlgo, int keyLen, String certB64) {
    }

    public record SignRequest(String keyAlias, String digestB64, String activationToken) {
    }

    public record SignResponse(String signatureB64) {
    }

    /** 보관 중인 서명키 목록(공개 정보만). */
    @GetMapping("/keys")
    public List<KeyView> keys() {
        return hsm.entries().entrySet().stream()
                .map(e -> toView(e.getKey(), e.getValue()))
                .toList();
    }

    /** SAM 활성화 토큰을 검증한 뒤 다이제스트에 서명한다. */
    @PostMapping("/sign")
    public SignResponse sign(@RequestBody SignRequest req) {
        if (req.activationToken() == null || !expectedActivationToken.equals(req.activationToken())) {
            // SAM 의 활성화 지시 없이는 서명키를 사용하지 않는다.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "HSM: 유효한 SAM 활성화 토큰이 없습니다");
        }
        try {
            byte[] digest = Base64.getDecoder().decode(req.digestB64());
            byte[] signature = hsm.sign(req.keyAlias(), digest);
            return new SignResponse(Base64.getEncoder().encodeToString(signature));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "HSM 서명 실패: " + e.getMessage());
        }
    }

    private KeyView toView(String alias, KeyEntry entry) {
        try {
            String certB64 = Base64.getEncoder().encodeToString(entry.certificate().getEncoded());
            return new KeyView(alias, entry.keyAlgo(), entry.keyLen(), certB64);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "인증서 인코딩 실패");
        }
    }
}
