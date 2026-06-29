package com.electcerti.krdss.dss.pki;

import com.electcerti.krdss.ades.cades.KrAdesOids;

/**
 * 특허-B(WebAuthn 기반 인증서 발급 인프라)에서 사용하는 OID 상수.
 *
 * <p>특허-A 검증 경로 정책({@link KrAdesOids#POLICY_WEBAUTHN} 등)을 재사용하되,
 * 본 특허에서 추가로 정의하는 <b>장치 보안 등급 정책(청구항 11)</b>과
 * <b>등록 기관(RA) 식별 정책(청구항 2·13)</b> OID를 모은다.</p>
 *
 * <p>사설 OID 아크 {@code 1.3.6.1.4.1.99999.3.*}는 임시값이며, 정식 PEN(Private
 * Enterprise Number) 배정 후 교체한다(특허-A {@link KrAdesOids} 와 동일 정책).</p>
 */
public final class KrPkiOids {

    private KrPkiOids() {
    }

    /** 특허-B(발급 인프라) 사설 서브트리. */
    public static final String PKI_ARC = KrAdesOids.KRDSS_ARC + ".3";

    // --- 장치 보안 등급 정책 OID (청구항 11: 등급 → Policy OID 차등) ---
    /** 등급 정책 서브트리. */
    public static final String GRADE_ARC = PKI_ARC + ".1";
    /** 고등급 인증기(예: 하드웨어 보안키, 검증된 attestation). */
    public static final String GRADE_HIGH = GRADE_ARC + ".1";
    /** 중등급 인증기. */
    public static final String GRADE_MEDIUM = GRADE_ARC + ".2";
    /** 저등급 인증기. */
    public static final String GRADE_LOW = GRADE_ARC + ".3";
    /** 등급 미상(attestation 미검증 또는 미등록 AAGUID). */
    public static final String GRADE_UNKNOWN = GRADE_ARC + ".4";

    // --- 등록 기관(RA) 식별 정책 OID 서브트리 (청구항 2·13) ---
    /** RA 식별 정책 서브트리. 각 RA는 이 아크 하위의 고유 OID를 갖는다. */
    public static final String RA_ARC = PKI_ARC + ".2";

    /** {@code RA_ARC} 하위의 RA 식별 정책 OID를 생성한다. */
    public static String raPolicy(String suffix) {
        return RA_ARC + "." + suffix;
    }

    // --- HSM Attestation 등급 정책 (청구항 15) — 후속(B-4) 예약 ---
    /** HSM 보안 등급 정책 서브트리(예약). */
    public static final String HSM_GRADE_ARC = PKI_ARC + ".3";
}
