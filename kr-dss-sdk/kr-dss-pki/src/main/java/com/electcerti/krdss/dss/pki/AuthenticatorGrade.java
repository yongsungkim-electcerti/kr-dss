package com.electcerti.krdss.dss.pki;

/**
 * 서명 생성 장치(WebAuthn 인증기)의 보안 등급 (특허-B 청구항 1 장치 등급 판정부, 청구항 11).
 *
 * <p>각 등급은 인증서 {@code certificatePolicies} 에 차등 부여되는 정책 OID와 1:1로 대응한다.</p>
 */
public enum AuthenticatorGrade {

    /** 고등급. */
    HIGH(KrPkiOids.GRADE_HIGH),
    /** 중등급. */
    MEDIUM(KrPkiOids.GRADE_MEDIUM),
    /** 저등급. */
    LOW(KrPkiOids.GRADE_LOW),
    /** 등급 미상(attestation 미검증 또는 미등록 AAGUID). */
    UNKNOWN(KrPkiOids.GRADE_UNKNOWN);

    private final String policyOid;

    AuthenticatorGrade(String policyOid) {
        this.policyOid = policyOid;
    }

    /** 이 등급에 대응하는 인증서 정책 OID(청구항 11). */
    public String policyOid() {
        return policyOid;
    }
}
