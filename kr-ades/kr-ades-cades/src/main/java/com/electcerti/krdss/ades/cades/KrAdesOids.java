package com.electcerti.krdss.ades.cades;

/**
 * 특허-A 구현에서 사용하는 OID 상수.
 *
 * <p>표준 CMS 속성 OID와, KR-DSS 사설 확장(서명 결속/검증 라우팅) OID를 모은다.
 * 사설 OID 아크 {@code 1.3.6.1.4.1.99999.*}는 임시값이며, 정식 PEN(Private
 * Enterprise Number) 배정 후 교체한다(특허-A 설계서 §4.1 참조).</p>
 */
public final class KrAdesOids {

    private KrAdesOids() {
    }

    // --- 표준 CMS SignedAttributes (RFC 5652 / RFC 5035) ---
    /** contentType. */
    public static final String CONTENT_TYPE = "1.2.840.113549.1.9.3";
    /** messageDigest = H(문서). */
    public static final String MESSAGE_DIGEST = "1.2.840.113549.1.9.4";
    /** signingTime = 서명 시각. */
    public static final String SIGNING_TIME = "1.2.840.113549.1.9.5";
    /** signingCertificateV2 = 서명자 인증서 해시(RFC 5035). */
    public static final String SIGNING_CERTIFICATE_V2 = "1.2.840.113549.1.9.16.2.47";
    /** id-data — 기본 eContentType. */
    public static final String ID_DATA = "1.2.840.113549.1.7.1";

    // --- KR-DSS 사설 확장(임시 아크) ---
    /** KR-DSS 사설 아크(임시). */
    public static final String KRDSS_ARC = "1.3.6.1.4.1.99999";

    /** WebAuthn 어서션 비서명 속성(unsignedAttrs) — WebAuthnAssertionAttr 컨테이너. */
    public static final String WEBAUTHN_ASSERTION_ATTR = KRDSS_ARC + ".1.1";
    /** WebAuthn 어서션을 서명값으로 사용함을 나타내는 signatureAlgorithm 식별자(라우팅 힌트). */
    public static final String WEBAUTHN_ASSERTION_SIG_ALG = KRDSS_ARC + ".1.2";

    // --- 검증 라우팅용 인증서 정책(certificatePolicies) OID (특허-A §5.2) ---
    /** WebAuthn 로컬 서명 검증 경로 정책. */
    public static final String POLICY_WEBAUTHN = KRDSS_ARC + ".2.1";
    /** HSM 원격 서명 검증 경로 정책. */
    public static final String POLICY_HSM = KRDSS_ARC + ".2.2";
    /** 표준 전자서명 검증 경로 정책. */
    public static final String POLICY_STANDARD = KRDSS_ARC + ".2.3";
}
