package com.electcerti.krdss.dss.core.verify;

import com.electcerti.krdss.ades.cades.bind.HashSuite;
import com.electcerti.krdss.ades.cades.bind.SignedAttrsParser;
import com.electcerti.krdss.ades.cades.container.WebAuthnAssertionAttr;
import com.electcerti.krdss.ades.cades.container.WebAuthnCmsAssembler;
import com.electcerti.krdss.ades.cades.container.WebAuthnCmsSignedData;
import com.electcerti.krdss.dss.api.VerificationStatus;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * 정책 기반 검증 라우터(Policy-Based Verification Router) — 특허-A 청구항 1/6/7/14.
 *
 * <p>전자서명 객체의 컨테이너 유형과 서명자 인증서의 정책 식별자에 따라 검증 경로를
 * 런타임에 동적으로 선택하고, 결과를 TOTAL_PASSED/INDETERMINATE/TOTAL_FAILED 로 분류한다.</p>
 *
 * <ul>
 *   <li>WebAuthn 모사 컨테이너로 파싱되면 → WebAuthn 로컬 서명 경로(Mode 1, T3) + 결속 검증</li>
 *   <li>그 외(JSON) → HSM/표준 경로(Mode 2)</li>
 * </ul>
 *
 * <p>경로 결정은 {@link CertPolicyResolver}(정책 OID)를 우선 참조하되, OID 가 없으면 컨테이너
 * 구조로 부트스트랩한다(설계서 §5.2). 특허-C(신뢰목록·폐지·시점·장치신뢰)는 본 단계에서
 * INDETERMINATE 사유 hook 으로만 반영한다.</p>
 */
public class VerificationRouter {

    private final WebAuthnVerificationPath webAuthnPath = new WebAuthnVerificationPath();
    private final HsmVerificationPath hsmPath = new HsmVerificationPath();
    private final WebAuthnCmsSignedData cmsSignedData = new WebAuthnCmsSignedData();
    private final WebAuthnCmsAssembler assembler = new WebAuthnCmsAssembler();

    /**
     * 검증 정책.
     *
     * @param rpId                    WebAuthn Relying Party ID
     * @param allowedOrigins          허용 origin(WEB https / AOS apk-key-hash)
     * @param userVerificationRequired UV 필수 여부
     * @param requireTrustList        신뢰목록 검증 필수 여부(특허-C, 미구현 시 INDETERMINATE)
     */
    public record Policy(String rpId, List<String> allowedOrigins,
                         boolean userVerificationRequired, boolean requireTrustList) {

        /** PoC 기본 정책(localhost, TL 미요구). */
        public static Policy demo() {
            return new Policy("localhost", List.of("http://localhost:8080"), false, false);
        }
    }

    /**
     * 전자서명 객체를 검증한다.
     *
     * @param signedObject     전자서명 객체(WebAuthn 모사 컨테이너 DER 또는 JSON)
     * @param originalDocument 원문(선택) — 문서 무결성 비교용
     * @param policy           검증 정책
     * @param store            WebAuthn 자격증명 레지스트리(CA 발급 인증서)
     */
    public VerificationResult verify(byte[] signedObject, byte[] originalDocument,
                                     Policy policy, WebAuthnCredentialStore store) {
        return verify(signedObject, originalDocument, policy, store, HashSuite.SHA_256);
    }

    /**
     * 해시 알고리즘을 명시해 검증한다(암호 민첩성, 특허-A 청구항 4/8).
     *
     * @param hashSuite 결속 challenge 재계산·문서 다이제스트 비교에 사용할 해시 알고리즘
     */
    public VerificationResult verify(byte[] signedObject, byte[] originalDocument,
                                     Policy policy, WebAuthnCredentialStore store, HashSuite hashSuite) {
        WebAuthnCmsAssembler.Parsed parsed = tryParseWebAuthn(signedObject);
        if (parsed != null) {
            return verifyWebAuthn(parsed, originalDocument, policy, store, hashSuite);
        }
        // 컨테이너가 WebAuthn 모사 구조가 아니면 HSM/표준 경로로 위임
        return hsmPath.verify(signedObject, originalDocument);
    }

    private WebAuthnCmsAssembler.Parsed tryParseWebAuthn(byte[] signedObject) {
        // 2단계 전략: 정식 CMS(2차) 우선 시도 → 모사 컨테이너(1차) 순으로 동일 Parsed 로 환원.
        try {
            return cmsSignedData.parse(signedObject);
        } catch (Exception ignored) {
            // 정식 CMS 구조가 아니면 모사 컨테이너로 위임
        }
        try {
            return assembler.parse(signedObject);
        } catch (Exception e) {
            return null;
        }
    }

    private VerificationResult verifyWebAuthn(WebAuthnCmsAssembler.Parsed parsed, byte[] document,
                                              Policy policy, WebAuthnCredentialStore store, HashSuite hashSuite) {
        List<VerificationResult.Check> checks = new ArrayList<>();
        WebAuthnAssertionAttr assertion = parsed.assertion();
        X509Certificate containerCert = parsed.certificates().isEmpty() ? null : parsed.certificates().get(0);

        // 0) 정책 식별자 라우팅(설계서 §5.2)
        CertPolicyResolver.Path policyPath = CertPolicyResolver.resolve(containerCert);
        checks.add(new VerificationResult.Check("정책 식별자 라우팅", true,
                policyPath != null ? "certificatePolicies → " + policyPath
                        : "정책 OID 없음 → 컨테이너 구조 기반 WEBAUTHN 부트스트랩"));

        String subject = containerCert != null ? containerCert.getSubjectX500Principal().getName() : null;

        // 1) 검증용 공개키 출처 = CA 발급 인증서(레지스트리). 미등록 시 컨테이너 인증서로 암호검증만.
        String credIdB64 = assertion.credentialId() != null ? b64url(assertion.credentialId()) : null;
        Optional<WebAuthnCredentialStore.StoredCredential> registered =
                credIdB64 != null ? store.find(credIdB64) : Optional.empty();

        WebAuthnCredentialStore.StoredCredential effective;
        if (registered.isPresent()) {
            effective = registered.get();
            if (containerCert != null && !containerCert.equals(effective.certificate())) {
                checks.add(new VerificationResult.Check("자격증명 인증서 결속", false,
                        "컨테이너 인증서가 등록된 CA 발급 인증서와 불일치"));
                return fail("CERTIFICATE_BINDING_FAILURE", subject, parsed, checks);
            }
            checks.add(new VerificationResult.Check("자격증명 등록 확인", true,
                    "등록된 CA 발급 인증서의 공개키 사용"));
        } else {
            if (containerCert == null) {
                checks.add(new VerificationResult.Check("자격증명 인증서", false, "인증서 없음"));
                return fail("NO_CERTIFICATE", subject, parsed, checks);
            }
            effective = new WebAuthnCredentialStore.StoredCredential(
                    containerCert, assertion.coseAlg(), assertion.aaguid(), 0L);
            checks.add(new VerificationResult.Check("자격증명 등록 확인", false,
                    "미등록 자격증명 — 컨테이너 인증서로 암호검증만 수행(신뢰 불충분)"));
        }

        // 2) WebAuthn 어서션 COSE 실검증(T3) — challenge 재계산/서명/flags/origin
        WebAuthnVerificationPath.Input input = new WebAuthnVerificationPath.Input(
                parsed.signedAttrsDer(), hashSuite,
                assertion.credentialId(), assertion.authenticatorData(),
                assertion.clientDataJSON(), parsed.signature(),
                policy.rpId(), policy.allowedOrigins(), policy.userVerificationRequired());
        WebAuthnVerificationPath.Result r = webAuthnPath.verify(input, effective);
        checks.add(new VerificationResult.Check("WebAuthn 어서션 서명", r.ok(),
                r.ok() ? "COSE 서명·flags·rpIdHash·challenge·origin 검증 성공" : r.reason()));
        if (!r.ok()) {
            return fail("SIG_CRYPTO_FAILURE", subject, parsed, checks);
        }

        // 3) 결속 검증 — SignedAttrs 파싱
        SignedAttrsParser.Parsed sa = SignedAttrsParser.parse(parsed.signedAttrsDer());
        String signingTime = sa.signingTime() != null ? sa.signingTime().toString() : null;

        // 3-1) 문서 무결성 — messageDigest == H(문서) (원문 제공 시)
        boolean digestOk = true;
        if (document != null && sa.messageDigest() != null) {
            byte[] h = hashSuite.digest(document);
            digestOk = Arrays.equals(h, sa.messageDigest());
            checks.add(new VerificationResult.Check("문서 무결성(messageDigest)", digestOk,
                    digestOk ? "원문 다이제스트 일치" : "원문 다이제스트 불일치(문서 변조)"));
        }

        // 3-2) 서명자 결속 — signingCertificateV2 해시 == H(서명자 인증서)
        boolean signerBindingOk = true;
        if (sa.signingCertHash() != null) {
            try {
                byte[] certHash = hashSuite.digest(effective.certificate().getEncoded());
                signerBindingOk = Arrays.equals(certHash, sa.signingCertHash());
            } catch (Exception e) {
                signerBindingOk = false;
            }
            checks.add(new VerificationResult.Check("서명자 결속(signingCertificateV2)", signerBindingOk,
                    signerBindingOk ? "서명자 인증서 해시 일치" : "서명자 인증서 해시 불일치"));
        }

        // 3-3) 인증서 유효기간(시점 일부)
        boolean certValid = isWithinValidity(effective.certificate());
        checks.add(new VerificationResult.Check("인증서 유효기간", certValid,
                certValid ? "유효기간 내" : "유효기간 외(만료 또는 미발효)"));

        // 3-4) 신뢰목록·폐지·장치신뢰 — 특허-C 범위(hook)
        checks.add(new VerificationResult.Check("신뢰목록·폐지·장치신뢰(특허-C)", !policy.requireTrustList(),
                policy.requireTrustList() ? "검증 필요(미구현)" : "PoC 미평가"));

        // 4) 결과 분류(특허-A §5.5)
        if (!digestOk || !signerBindingOk) {
            return new VerificationResult(VerificationStatus.TOTAL_FAILED,
                    !digestOk ? "HASH_FAILURE" : "SIGNER_BINDING_FAILURE",
                    "WEBAUTHN", subject, signingTime, checks);
        }
        if (registered.isEmpty() || !certValid || policy.requireTrustList()) {
            String sub = registered.isEmpty() ? "CREDENTIAL_NOT_REGISTERED"
                    : (!certValid ? "CERTIFICATE_OUT_OF_VALIDITY" : "TRUST_LIST_NOT_EVALUATED");
            return new VerificationResult(VerificationStatus.INDETERMINATE, sub,
                    "WEBAUTHN", subject, signingTime, checks);
        }
        return new VerificationResult(VerificationStatus.TOTAL_PASSED, null,
                "WEBAUTHN", subject, signingTime, checks);
    }

    private VerificationResult fail(String subIndication, String subject,
                                    WebAuthnCmsAssembler.Parsed parsed, List<VerificationResult.Check> checks) {
        String signingTime = null;
        try {
            SignedAttrsParser.Parsed sa = SignedAttrsParser.parse(parsed.signedAttrsDer());
            signingTime = sa.signingTime() != null ? sa.signingTime().toString() : null;
        } catch (Exception ignored) {
            // 파싱 실패 시 시각 미상
        }
        return new VerificationResult(VerificationStatus.TOTAL_FAILED, subIndication,
                "WEBAUTHN", subject, signingTime, checks);
    }

    private boolean isWithinValidity(X509Certificate cert) {
        try {
            Instant now = Instant.now();
            return !now.isBefore(cert.getNotBefore().toInstant())
                    && !now.isAfter(cert.getNotAfter().toInstant());
        } catch (Exception e) {
            return false;
        }
    }

    private String b64url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
