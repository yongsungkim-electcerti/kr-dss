package com.electcerti.krdss.dss.core.verify;

import com.webauthn4j.WebAuthnManager;
import com.webauthn4j.authenticator.Authenticator;
import com.webauthn4j.authenticator.AuthenticatorImpl;
import com.webauthn4j.data.AuthenticationData;
import com.webauthn4j.data.AuthenticationParameters;
import com.webauthn4j.data.AuthenticationRequest;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.authenticator.AttestedCredentialData;
import com.webauthn4j.data.attestation.authenticator.COSEKey;
import com.webauthn4j.data.attestation.authenticator.EC2COSEKey;
import com.webauthn4j.data.attestation.authenticator.RSACOSEKey;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.Challenge;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.server.ServerProperty;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

import com.electcerti.krdss.ades.cades.bind.HashSuite;

/**
 * 정책 기반 검증 라우터 — WebAuthn 기반 검증 경로 (특허-A 청구항 12/15).
 *
 * <p>WebAuthn 로컬 서명(Mode 1)의 어서션을 검증한다. 검증용 공개키는
 * {@link WebAuthnCredentialStore}에 보관된 <b>CA 발급 인증서</b>에서 가져오며,
 * SAM/HSM 원격서명 경로를 경유하지 않는다(설계서 §2.4).</p>
 *
 * <p>핵심 단계(webauthn4j 위임):</p>
 * <ol>
 *   <li>결속 challenge 재계산 = HashFunc(DER(SignedAttrs)) — clientDataJSON.challenge 비교</li>
 *   <li>clientDataJSON.type/origin, authenticatorData.rpIdHash, UP/UV 플래그 검증</li>
 *   <li>authenticatorData ∥ H(clientDataJSON) 에 대한 어서션 서명을 인증서 공개키로 검증</li>
 * </ol>
 */
public class WebAuthnVerificationPath {

    private final WebAuthnManager manager = WebAuthnManager.createNonStrictWebAuthnManager();

    /** 검증 입력. */
    public record Input(
            byte[] signedAttrsDer,
            HashSuite hashSuite,
            byte[] credentialId,
            byte[] authenticatorData,
            byte[] clientDataJSON,
            byte[] signature,
            String rpId,
            List<String> allowedOrigins,
            boolean userVerificationRequired) {
    }

    /** 검증 결과. */
    public record Result(boolean ok, String reason, long observedSignCount) {
        static Result fail(String reason) {
            return new Result(false, reason, -1);
        }
    }

    /**
     * 어서션을 검증한다.
     *
     * @param input    검증 입력(결속 SignedAttrs + 어서션 + 정책)
     * @param stored   레지스트리에 보관된 자격증명(CA 발급 인증서 포함)
     */
    public Result verify(Input input, WebAuthnCredentialStore.StoredCredential stored) {
        if (stored == null) {
            return Result.fail("등록되지 않은 WebAuthn 자격증명");
        }
        // 1) 결속 challenge 재계산 (특허-A §5.3-2). webauthn4j 가 clientDataJSON.challenge 와 대조.
        byte[] challengeBytes;
        try {
            challengeBytes = input.hashSuite().digest(input.signedAttrsDer());
        } catch (Exception e) {
            return Result.fail("결속 challenge 계산 실패: " + e.getMessage());
        }
        Challenge challenge = new DefaultChallenge(challengeBytes);

        // 2) origin 결정 — 허용목록과 대조 후 ServerProperty 구성 (WEB https / AOS apk-key-hash)
        Origin origin;
        try {
            origin = resolveOrigin(input);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
        ServerProperty serverProperty = new ServerProperty(origin, input.rpId(), challenge, null);

        // 3) 인증서 공개키 → COSEKey → Authenticator (검증용 공개키 출처 = CA 발급 인증서)
        Authenticator authenticator;
        try {
            COSEKey coseKey = toCoseKey(stored.certificate(), stored.coseAlg());
            AttestedCredentialData acd = new AttestedCredentialData(
                    new AAGUID(stored.aaguid() != null ? stored.aaguid() : new byte[16]),
                    input.credentialId(), coseKey);
            authenticator = new AuthenticatorImpl(acd, null, stored.signCount());
        } catch (Exception e) {
            return Result.fail("자격증명 공개키 처리 실패: " + e.getMessage());
        }

        // 4) webauthn4j 검증 — 서명/flags/rpIdHash/challenge/origin/counter
        try {
            AuthenticationRequest request = new AuthenticationRequest(
                    input.credentialId(), input.authenticatorData(), input.clientDataJSON(), input.signature());
            AuthenticationParameters parameters = new AuthenticationParameters(
                    serverProperty, authenticator, null,
                    input.userVerificationRequired(), true);
            AuthenticationData data = manager.parse(request);
            manager.validate(data, parameters);
            long observed = data.getAuthenticatorData() != null
                    ? data.getAuthenticatorData().getSignCount() : stored.signCount();
            return new Result(true, null, observed);
        } catch (Exception e) {
            return Result.fail("WebAuthn 어서션 검증 실패: " + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? " - " + e.getMessage() : ""));
        }
    }

    private Origin resolveOrigin(Input input) {
        String originStr = parseOrigin(input.clientDataJSON());
        if (originStr == null) {
            throw new IllegalArgumentException("clientDataJSON.origin 누락");
        }
        if (input.allowedOrigins() != null && !input.allowedOrigins().isEmpty()
                && !input.allowedOrigins().contains(originStr)) {
            throw new IllegalArgumentException("허용되지 않은 origin: " + originStr);
        }
        return new Origin(originStr);
    }

    /** clientDataJSON 에서 origin 만 가볍게 추출(webauthn4j 가 본 검증을 재수행). */
    private String parseOrigin(byte[] clientDataJSON) {
        try {
            String json = new String(clientDataJSON, java.nio.charset.StandardCharsets.UTF_8);
            int i = json.indexOf("\"origin\"");
            if (i < 0) {
                return null;
            }
            int colon = json.indexOf(':', i);
            int q1 = json.indexOf('"', colon + 1);
            int q2 = json.indexOf('"', q1 + 1);
            return (q1 < 0 || q2 < 0) ? null : json.substring(q1 + 1, q2);
        } catch (Exception e) {
            return null;
        }
    }

    private COSEKey toCoseKey(X509Certificate cert, int coseAlg) {
        PublicKey pub = cert.getPublicKey();
        if (pub instanceof ECPublicKey ec) {
            return EC2COSEKey.create(ec, COSEAlgorithmIdentifier.create(
                    coseAlg != 0 ? coseAlg : COSEAlgorithmIdentifier.ES256.getValue()));
        }
        if (pub instanceof RSAPublicKey rsa) {
            return RSACOSEKey.create(rsa, COSEAlgorithmIdentifier.create(
                    coseAlg != 0 ? coseAlg : COSEAlgorithmIdentifier.RS256.getValue()));
        }
        throw new IllegalArgumentException("지원하지 않는 공개키 유형: " + pub.getAlgorithm());
    }
}
