package com.electcerti.krdss.poc.rssp;

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
 * CSC v2 API 엔드포인트.
 *
 * <p>이용사(SIC)는 문서 전체가 아닌 <b>다이제스트만</b> 전송한다. RSSP 는 자격증명
 * 조회를 위해 HSM 을, 서명활성화·서명연산을 위해 SAM 을 호출한다.</p>
 *
 * <pre>
 *   [이용사 SIC] --signHash(hash,SAD)--> [RSSP] --activate--> [SAM] --sign--> [HSM]
 * </pre>
 */
@RestController
@RequestMapping("/csc/v2")
public class RsspController {

    /** 데모: 자격증명 식별자 == HSM 키 별칭. */
    private static final String DEMO_CREDENTIAL = "krdss-remote-signer";

    private final RestClient sam;
    private final RestClient hsm;

    public RsspController(
            @Value("${krdss.rssp.sam-base-url:http://localhost:8091}") String samBaseUrl,
            @Value("${krdss.rssp.hsm-base-url:http://localhost:8092}") String hsmBaseUrl) {
        this.sam = RestClient.create(samBaseUrl);
        this.hsm = RestClient.create(hsmBaseUrl);
    }

    public record CredentialIdRequest(String credentialID) {
    }

    private record HsmKeyView(String alias, String keyAlgo, int keyLen, String certB64) {
    }

    private record SamActivateRequest(String credentialID, String keyAlias, List<String> hashes, String sad) {
    }

    private record SamActivateResponse(List<String> signatures) {
    }

    private record SamAuthorizeRequest(
            String credentialID, List<String> hashes, String signerId, String pin, String otp) {
    }

    private record SamAuthorizeResponse(String sad, long expiresInSec) {
    }

    /** 서명자 2요소 인증 → SAM 이 SAD 토큰 발급. (CSC credentials/authorize) */
    @PostMapping("/credentials/authorize")
    public AuthorizeResponse credentialsAuthorize(@RequestBody AuthorizeRequest req) {
        try {
            SamAuthorizeResponse res = sam.post()
                    .uri("/sam/authorize")
                    .body(new SamAuthorizeRequest(
                            req.credentialID(), req.hashes(), req.signerId(), req.pin(), req.otp()))
                    .retrieve()
                    .body(SamAuthorizeResponse.class);
            if (res == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SAM 빈 응답");
            }
            return new AuthorizeResponse(res.sad(), res.expiresInSec());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "서명자 인증 실패");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SAM 인증 호출 실패: " + e.getMessage());
        }
    }

    /** 서명자가 사용할 수 있는 자격증명 목록. */
    @PostMapping("/credentials/list")
    public CredentialsListResponse credentialsList() {
        List<HsmKeyView> keys = fetchHsmKeys();
        return new CredentialsListResponse(keys.stream().map(HsmKeyView::alias).toList());
    }

    /** 자격증명의 인증서·키 정보. */
    @PostMapping("/credentials/info")
    public CredentialInfo credentialsInfo(@RequestBody CredentialIdRequest req) {
        HsmKeyView key = fetchHsmKeys().stream()
                .filter(k -> k.alias().equals(req.credentialID()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알 수 없는 자격증명"));
        return new CredentialInfo(
                key.alias(),
                "KR-DSS 원격서명 데모 자격증명",
                key.keyAlgo(),
                key.keyLen(),
                List.of(key.certB64()),
                "enabled");
    }

    /** 서명자 패스키 등록 → SAM. (CSC passkey/register) */
    @PostMapping("/passkey/register")
    public PasskeyRegisterResponse passkeyRegister(@RequestBody PasskeyRegisterRequest req) {
        return relay("/sam/passkey/register", req, PasskeyRegisterResponse.class);
    }

    /** WebAuthn 인증 챌린지 발급 → SAM. (CSC credentials/authorize/begin) */
    @PostMapping("/credentials/authorize/begin")
    public AuthorizeBeginResponse authorizeBegin(@RequestBody AuthorizeBeginRequest req) {
        return relay("/sam/authorize/begin", req, AuthorizeBeginResponse.class);
    }

    /** WebAuthn 어서션 + PIN 검증 → SAD 발급(→ SAM). (CSC credentials/authorize/finish) */
    @PostMapping("/credentials/authorize/finish")
    public AuthorizeResponse authorizeFinish(@RequestBody AuthorizeFinishRequest req) {
        return relay("/sam/authorize/finish", req, AuthorizeResponse.class);
    }

    /** SAM 으로 요청을 중계하고 인증 실패(401)는 그대로 전파한다. */
    private <T> T relay(String samPath, Object body, Class<T> type) {
        try {
            T res = sam.post().uri(samPath).body(body).retrieve().body(type);
            if (res == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SAM 빈 응답");
            }
            return res;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "서명자 인증 실패");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SAM 호출 실패: " + e.getMessage());
        }
    }

    /** 다이제스트 + SAD → SAM 활성화 → HSM 서명 → 서명값. */
    @PostMapping("/signatures/signHash")
    public SignHashResponse signHash(@RequestBody SignHashRequest req) {
        if (req.hashes() == null || req.hashes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "서명할 다이제스트가 없습니다");
        }
        try {
            SamActivateResponse res = sam.post()
                    .uri("/sam/signatures/signHash")
                    .body(new SamActivateRequest(req.credentialID(), req.credentialID(), req.hashes(), req.sad()))
                    .retrieve()
                    .body(SamActivateResponse.class);
            if (res == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SAM 빈 응답");
            }
            return new SignHashResponse(res.signatures());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "SAM 호출 실패: " + e.getMessage());
        }
    }

    private List<HsmKeyView> fetchHsmKeys() {
        try {
            HsmKeyView[] keys = hsm.get().uri("/hsm/keys").retrieve().body(HsmKeyView[].class);
            return keys == null ? List.of() : List.of(keys);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "HSM 키 조회 실패: " + e.getMessage());
        }
    }
}
