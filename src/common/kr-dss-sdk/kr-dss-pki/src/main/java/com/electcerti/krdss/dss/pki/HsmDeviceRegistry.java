package com.electcerti.krdss.dss.pki;

/**
 * hsmDeviceId → HSM 보안 등급 조회 (특허-B 청구항 15; 특허-C KR-TL HSM 서브목록 참조).
 *
 * <p>특허-C 의 통합 신뢰목록(KR-TL) HSM 서브목록은 hsmDeviceId 로 HSM 제조사·모델·하드웨어
 * 보안 수준(CC EAL/FIPS 등급)을 조회한다. 본 인터페이스는 그 조회를 추상화하며, 운영 환경에서는
 * KR-TL 연동 구현(특허-C)으로 교체할 수 있다.</p>
 */
public interface HsmDeviceRegistry {

    /**
     * 주어진 hsmDeviceId 의 HSM 보안 등급을 반환한다.
     *
     * @param hsmDeviceId HSM 장치 모델 식별자(null/길이 0 이면 미상)
     * @return 보안 등급(미등록 시 {@link HsmGrade#UNKNOWN})
     */
    HsmGrade gradeOf(byte[] hsmDeviceId);
}
