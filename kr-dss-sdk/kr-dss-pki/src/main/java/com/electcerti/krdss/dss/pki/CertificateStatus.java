package com.electcerti.krdss.dss.pki;

/**
 * 인증서 생명주기 상태 (특허-B 청구항 9, 발명 B-3).
 */
public enum CertificateStatus {
    /** 유효. */
    VALID,
    /** 정지(재개 가능). */
    SUSPENDED,
    /** 폐지(종료 상태). */
    REVOKED
}
