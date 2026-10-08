package com.electcerti.krdss.poc.rp;

import com.electcerti.krdss.ades.core.KrAdesFormat;
import com.electcerti.krdss.ades.core.KrAdesLevel;
import com.electcerti.krdss.ades.core.PackagingType;
import com.electcerti.krdss.dss.api.Dtos.SignResult;
import com.electcerti.krdss.dss.remote.RemoteSignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

/**
 * 이용사 서비스의 원격전자서명 데모 엔드포인트.
 *
 * <p>이용자가 문서(텍스트)를 제출하면 SIC 가 원격전자서명을 수행하여 KR-AdES
 * 서명객체를 돌려준다. 문서 원본은 RSSP 로 전송되지 않고 다이제스트만 전송된다.</p>
 */
@RestController
@RequestMapping("/rp")
public class RpRemoteSignController {

    private final SignerInteractionComponent sic;
    private final String defaultCredentialId;

    public RpRemoteSignController(SignerInteractionComponent sic,
                                  @Value("${krdss.rp.credential-id:krdss-remote-signer}") String defaultCredentialId) {
        this.sic = sic;
        this.defaultCredentialId = defaultCredentialId;
    }

    /**
     * @param text         서명할 문서 내용
     * @param credentialID 자격증명(선택, 기본값 사용)
     * @param format       KR-AdES 포맷(선택, 기본 MADES)
     * @param level        서명 레벨(선택, 기본 KR_B)
     * @param packaging    패키징(선택, 기본 ENVELOPING)
     */
    public record SignDocumentRequest(
            String text, String credentialID, String format, String level, String packaging) {
    }

    public record SignDocumentResponse(String format, String level, String signedDocument) {
    }

    @PostMapping("/remote-sign")
    public SignDocumentResponse remoteSign(@RequestBody SignDocumentRequest req) {
        if (req.text() == null || req.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "서명할 문서 text 가 비어 있습니다");
        }
        KrAdesFormat format = parse(KrAdesFormat.class, req.format(), KrAdesFormat.MADES);
        KrAdesLevel level = parse(KrAdesLevel.class, req.level(), KrAdesLevel.KR_B);
        PackagingType packaging = parse(PackagingType.class, req.packaging(), PackagingType.ENVELOPING);
        String credentialId = (req.credentialID() == null || req.credentialID().isBlank())
                ? defaultCredentialId : req.credentialID();

        try {
            SignResult result = sic.remoteSign(
                    req.text().getBytes(StandardCharsets.UTF_8), credentialId, format, level, packaging);
            return new SignDocumentResponse(
                    result.format().profileName(),
                    result.level().code(),
                    new String(result.signedDocument(), StandardCharsets.UTF_8));
        } catch (RemoteSignException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "원격서명 실패: " + e.getMessage());
        }
    }

    private <E extends Enum<E>> E parse(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "알 수 없는 " + type.getSimpleName() + ": " + value);
        }
    }
}
