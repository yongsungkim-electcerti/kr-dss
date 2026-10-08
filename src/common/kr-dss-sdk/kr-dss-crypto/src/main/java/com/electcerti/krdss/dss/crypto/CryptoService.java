package com.electcerti.krdss.dss.crypto;

/**
 * 보안·크립토 공통 서비스.
 *
 * <p>해시 계산, 인증서 경로 검증, 폐기상태 확인(OCSP/CRL), 타임스탬프(TSA) 연동을
 * 담당한다. 서명 생성·검증 모듈이 공통으로 의존한다.</p>
 */
public interface CryptoService {

    /** 지정 알고리즘으로 다이제스트를 계산한다. */
    byte[] digest(byte[] data, String algorithm);

    /** 인증서 경로 및 폐기상태를 검증한다. */
    boolean validateCertificatePath(byte[] certificate);
}
