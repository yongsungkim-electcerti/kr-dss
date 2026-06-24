package com.electcerti.krdss.dss.remote;

import java.util.List;

/**
 * CSC(Cloud Signature Consortium) v2 API 데이터 모델 모음.
 *
 * <p>원격전자서명(원격 QSCD) 표준 인터페이스의 요청/응답 형태를 정의한다.
 * eIDAS 원격서명 및 EN 419 241(서버서명) 모델에 정합하며, 스캐폴딩 단계의
 * 최소 필드만 포함한다. 세부 필드는 설계 단계에서 확장한다.</p>
 *
 * @see <a href="https://cloudsignatureconsortium.org/">Cloud Signature Consortium API v2</a>
 */
public final class CscModels {

    private CscModels() {
    }

    /** 서명자 자격증명(서명키+인증서) 식별 정보. {@code credentials/info} 응답. */
    public record CredentialInfo(
            String credentialID,
            String description,
            String keyAlgo,        // 예: RSA, EC
            int keyLen,            // 키 길이(bit)
            List<String> certChainB64,  // Base64 인증서 체인 (서명용 인증서 우선)
            String status          // enabled / disabled
    ) {
    }

    /** {@code credentials/list} 응답 — 서명자가 사용할 수 있는 자격증명 목록. */
    public record CredentialsListResponse(List<String> credentialIDs) {
    }

    /**
     * {@code credentials/authorize} 요청 — 서명자 인증·인가로 SAD 를 발급받는다.
     *
     * <p>CSC 의 인증/인가 단계. 서명자 2요소 인증(아는 것 PIN + 가진 것 TOTP)을
     * 서명 대상 다이제스트에 묶어 제출한다. SAM 이 검증 후 SAD 토큰을 발급한다.</p>
     *
     * @param credentialID 자격증명 식별자
     * @param hashes       서명 대상 다이제스트(Base64) 목록 — SAD 에 바인딩
     * @param signerId     서명자 식별자
     * @param pin          서명자 PIN(아는 것)
     * @param otp          TOTP 코드(가진 것)
     */
    public record AuthorizeRequest(
            String credentialID,
            List<String> hashes,
            String signerId,
            String pin,
            String otp
    ) {
    }

    /**
     * {@code credentials/authorize} 응답 — 발급된 SAD 토큰.
     *
     * @param sad          SAM 이 서명한 SAD 토큰(JWS 컴팩트 문자열)
     * @param expiresInSec 만료까지 남은 시간(초)
     */
    public record AuthorizeResponse(String sad, long expiresInSec) {
    }

    /**
     * OAuth2 토큰 응답(서비스 인증, client_credentials grant).
     *
     * @param accessToken  Bearer 액세스 토큰
     * @param tokenType    토큰 유형(고정: Bearer)
     * @param expiresInSec 만료까지 남은 시간(초)
     */
    public record TokenResponse(String accessToken, String tokenType, long expiresInSec) {
    }

    // --- WebAuthn 기반 서명자 인증(FIDO2 패스키/생체) ---

    /** 패스키 등록 요청 — 서명자와 인증기 자격증명 ID 를 연결한다. */
    public record PasskeyRegisterRequest(String signerId, String credentialId, String clientDataJSON) {
    }

    /** 패스키 등록 응답. */
    public record PasskeyRegisterResponse(boolean registered, String message) {
    }

    /**
     * {@code credentials/authorize/begin} 요청 — WebAuthn 인증 챌린지 발급 요청.
     *
     * <p>SAM 이 서명 대상 해시에 바인딩된 챌린지를 발급한다(서명-인증 결착).</p>
     */
    public record AuthorizeBeginRequest(String credentialID, List<String> hashes, String signerId) {
    }

    /**
     * {@code credentials/authorize/begin} 응답 — WebAuthn {@code navigator.credentials.get} 파라미터.
     *
     * @param challenge       Base64URL 챌린지(해시 바인딩)
     * @param rpId            Relying Party ID (예: localhost)
     * @param allowCredentials 서명자에게 등록된 자격증명 ID(Base64URL) 목록
     * @param timeoutMs       인증 타임아웃(ms)
     */
    public record AuthorizeBeginResponse(
            String challenge, String rpId, List<String> allowCredentials, long timeoutMs) {
    }

    /**
     * {@code credentials/authorize/finish} 요청 — WebAuthn 어서션 제출.
     *
     * <p>지식요소(PIN)와 소유/생체요소(WebAuthn 어서션)를 함께 제출해 2요소 인증을 완성한다.
     * SAM 이 검증 후 SAD 토큰을 발급한다.</p>
     *
     * @param credentialID      자격증명 식별자(KR-DSS)
     * @param hashes            서명 대상 다이제스트(Base64)
     * @param signerId          서명자 식별자
     * @param pin               서명자 PIN(아는 것)
     * @param webauthnCredId    사용된 WebAuthn 자격증명 ID(Base64URL)
     * @param clientDataJSON    WebAuthn clientDataJSON(Base64URL)
     * @param authenticatorData WebAuthn authenticatorData(Base64URL)
     * @param signature         WebAuthn 어서션 서명(Base64URL)
     */
    public record AuthorizeFinishRequest(
            String credentialID,
            List<String> hashes,
            String signerId,
            String pin,
            String webauthnCredId,
            String clientDataJSON,
            String authenticatorData,
            String signature
    ) {
    }

    /**
     * {@code signatures/signHash} 요청.
     *
     * @param credentialID 사용할 자격증명 식별자
     * @param hashes       서명 대상 다이제스트(Base64) 목록 — 문서 전체가 아닌 해시만 전송
     * @param hashAlgoOid  다이제스트 알고리즘 OID (예: 2.16.840.1.101.3.4.2.1 = SHA-256)
     * @param signAlgoOid  서명 알고리즘 OID (예: 1.2.840.113549.1.1.11 = SHA256withRSA)
     * @param sad          서명활성화데이터(직렬화된 토큰) — sole control 증빙
     */
    public record SignHashRequest(
            String credentialID,
            List<String> hashes,
            String hashAlgoOid,
            String signAlgoOid,
            String sad
    ) {
    }

    /** {@code signatures/signHash} 응답 — 해시별 서명값(Base64). */
    public record SignHashResponse(List<String> signatures) {
    }

    /** 표준 OID 상수. */
    public static final class Oid {
        private Oid() {
        }

        public static final String SHA256 = "2.16.840.1.101.3.4.2.1";
        public static final String SHA384 = "2.16.840.1.101.3.4.2.2";
        public static final String SHA512 = "2.16.840.1.101.3.4.2.3";
        public static final String SHA256_WITH_RSA = "1.2.840.113549.1.1.11";
        public static final String ECDSA_WITH_SHA256 = "1.2.840.10045.4.3.2";
    }
}
