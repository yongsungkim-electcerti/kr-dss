package com.electcerti.krdss.dss.pki;

/**
 * HSM 장치의 보안 등급 (특허-B 청구항 15, 발명 B-4).
 *
 * <p>HSM Attestation 검증 결과에서 확인된 HSM 보안 수준(CC EAL / FIPS 등급, 키 비추출성)을
 * 등급화한다. 각 등급은 인증서 {@code certificatePolicies} 에 차등 부여되는 HSM 정책 OID와
 * 1:1로 대응한다. 등급 산출 기준 메타데이터(hsmDeviceId → 등급)는 특허-C(KR-TL HSM 서브목록)를
 * 참조한다.</p>
 */
public enum HsmGrade {

    /** 고등급. */
    HIGH(KrPkiOids.HSM_GRADE_HIGH),
    /** 중등급. */
    MEDIUM(KrPkiOids.HSM_GRADE_MEDIUM),
    /** 저등급. */
    LOW(KrPkiOids.HSM_GRADE_LOW),
    /** 등급 미상(attestation 미검증 또는 미등록 hsmDeviceId). */
    UNKNOWN(KrPkiOids.HSM_GRADE_UNKNOWN);

    private final String policyOid;

    HsmGrade(String policyOid) {
        this.policyOid = policyOid;
    }

    /** 이 등급에 대응하는 인증서 정책 OID(청구항 15). */
    public String policyOid() {
        return policyOid;
    }
}
