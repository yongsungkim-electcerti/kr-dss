package com.electcerti.krdss.poc.rp;

import com.electcerti.krdss.ades.core.KrAdesFormat;
import com.electcerti.krdss.ades.core.KrAdesLevel;
import com.electcerti.krdss.ades.core.PackagingType;
import com.electcerti.krdss.dss.api.Dtos.SignRequest;
import com.electcerti.krdss.dss.api.Dtos.SignResult;
import com.electcerti.krdss.dss.core.remote.RemoteSignCoordinator;
import com.electcerti.krdss.dss.core.remote.RemoteSignature;
import com.electcerti.krdss.dss.remote.CscModels.AuthorizeRequest;
import com.electcerti.krdss.dss.remote.CscModels.AuthorizeResponse;
import com.electcerti.krdss.dss.remote.CscModels.CredentialInfo;
import com.electcerti.krdss.dss.remote.CscModels.Oid;
import com.electcerti.krdss.dss.remote.CscModels.SignHashRequest;
import com.electcerti.krdss.dss.remote.CscModels.SignHashResponse;
import com.electcerti.krdss.dss.remote.RemoteSignClient;
import com.electcerti.krdss.dss.remote.RemoteSignException;
import com.electcerti.krdss.dss.remote.Totp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

/**
 * 이용사 서명요소 (SIC, Signer's Interaction Component).
 *
 * <p>원격전자서명에서 <b>서명자 측 단말</b> 역할을 하며 CSC 2계층 인증을 수행한다:</p>
 * <ol>
 *   <li><b>서비스 인증</b> — OAuth2 client_credentials 로 RSSP 액세스 토큰 획득</li>
 *   <li><b>서명자 인증·인가</b> — 다이제스트에 PIN+TOTP 를 묶어 제출, SAM 이 발급한 SAD 수령</li>
 *   <li><b>서명</b> — SAD 로 서명값 수령 후 KR-AdES 서명객체로 결합</li>
 * </ol>
 *
 * <p>문서 원본은 로컬에 두고 다이제스트만 전송한다. 데모에서는 TOTP 를 설정된 비밀로
 * 생성하여 자동 시연하지만, 실제로는 서명자가 인증앱의 코드를 직접 입력한다.</p>
 */
@Component
public class SignerInteractionComponent {

    private final RemoteSignClient rssp;
    private final RemoteSignCoordinator coordinator = new RemoteSignCoordinator();

    // OAuth2 서비스 클라이언트 자격
    private final String clientId;
    private final String clientSecret;

    // 서명자 2요소 인증 자격(데모)
    private final String signerId;
    private final String pin;
    private final String totpSecret;

    public SignerInteractionComponent(
            @Value("${krdss.rp.rssp-base-url:http://localhost:8090}") String rsspBaseUrl,
            @Value("${krdss.rp.oauth.client-id:krdss-rp-client}") String clientId,
            @Value("${krdss.rp.oauth.client-secret:rp-client-secret}") String clientSecret,
            @Value("${krdss.rp.signer.id:signer-001}") String signerId,
            @Value("${krdss.rp.signer.pin:123456}") String pin,
            @Value("${krdss.rp.signer.totp-secret:JBSWY3DPEHPK3PXP}") String totpSecret) {
        this.rssp = new RemoteSignClient(rsspBaseUrl);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.signerId = signerId;
        this.pin = pin;
        this.totpSecret = totpSecret;
    }

    /**
     * 문서를 원격전자서명하여 KR-AdES 서명객체를 생성한다.
     *
     * @param document     서명 대상 문서(원본은 로컬에만 존재)
     * @param credentialID 사용할 자격증명
     * @param format       KR-AdES 포맷
     * @param level        서명 레벨
     * @param packaging    패키징 방식
     */
    public SignResult remoteSign(byte[] document, String credentialID,
                                 KrAdesFormat format, KrAdesLevel level, PackagingType packaging) {
        // 0) 서비스 인증 — OAuth2 액세스 토큰 획득(이후 CSC 호출에 Bearer 첨부)
        rssp.obtainAccessToken(clientId, clientSecret);

        // 1) 자격증명(서명 인증서) 조회 — 서명객체에 포함하여 검증에 사용.
        CredentialInfo credential = rssp.credentialInfo(credentialID);
        if (credential.certChainB64() == null || credential.certChainB64().isEmpty()) {
            throw new RemoteSignException("자격증명에 서명 인증서가 없습니다: " + credentialID);
        }
        byte[] signingCertificate = Base64.getDecoder().decode(credential.certChainB64().get(0));

        // 2) 코어 구동 — 콜백 안에서 다이제스트를 받아 서명자 인증(2FA)을 수행하고 서명한다.
        SignRequest request = new SignRequest(document, format, level, packaging);
        return coordinator.sign(request, (digest, digestAlgorithm) -> {
            String hashB64 = Base64.getEncoder().encodeToString(digest);

            // 2-1) 서명자 인증·인가 — 다이제스트에 PIN+TOTP 를 바인딩해 SAD 발급.
            //      (TOTP 는 서명자 인증앱의 현재 코드를 데모에서 직접 생성)
            String otp = Totp.generate(totpSecret, Instant.now().getEpochSecond());
            AuthorizeResponse auth = rssp.authorize(new AuthorizeRequest(
                    credentialID, List.of(hashB64), signerId, pin, otp));

            // 2-2) 발급된 SAD 로 서명 요청.
            SignHashResponse response = rssp.signHash(new SignHashRequest(
                    credentialID, List.of(hashB64), Oid.SHA256, Oid.SHA256_WITH_RSA, auth.sad()));
            if (response.signatures() == null || response.signatures().isEmpty()) {
                throw new RemoteSignException("RSSP 가 서명값을 반환하지 않았습니다");
            }
            byte[] signatureValue = Base64.getDecoder().decode(response.signatures().get(0));
            return new RemoteSignature(signatureValue, signingCertificate);
        });
    }
}
