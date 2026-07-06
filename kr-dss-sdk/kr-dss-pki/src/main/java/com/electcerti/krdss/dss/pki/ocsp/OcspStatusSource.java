package com.electcerti.krdss.dss.pki.ocsp;

import java.math.BigInteger;

/**
 * 일련번호(serial) → 인증서 폐지 상태 조회 (RFC 6960 응답 데이터 원천).
 *
 * <p>{@link OcspResponder}는 폐지 정보의 저장 방식을 알 필요 없이 이 인터페이스로만 상태를
 * 조회한다. 구현체는 발급 이력·생명주기 관리부(예: {@code CertificateLifecycleManager})나
 * 별도 폐지 대장을 조회해 {@link OcspCertStatus}를 반환한다. 알지 못하는 일련번호는
 * {@link OcspCertStatus#unknown()}을 반환한다.</p>
 */
@FunctionalInterface
public interface OcspStatusSource {

    /** 주어진 일련번호의 현재 폐지 상태. 미발급·미추적이면 {@link OcspCertStatus#unknown()}. */
    OcspCertStatus lookup(BigInteger serialNumber);
}
