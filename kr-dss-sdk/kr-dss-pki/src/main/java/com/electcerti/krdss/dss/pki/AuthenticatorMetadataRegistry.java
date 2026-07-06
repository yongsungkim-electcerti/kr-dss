package com.electcerti.krdss.dss.pki;

/**
 * AAGUID → 인증기 보안 등급 조회 (특허-B 청구항 12).
 *
 * <p>인증기 메타데이터 레지스트리는 AAGUID 로 인증기의 보안 등급·인증 방식·하드웨어
 * 보안 수준을 조회한다. 일실시예에서 본 레지스트리는 FIDO Alliance 가 운영하는
 * FIDO Metadata Service(MDS) 로 구현될 수 있다(청구항 12). 인증기 메타데이터의
 * 상세 구조는 특허-C(통합 신뢰목록)를 참조한다.</p>
 */
public interface AuthenticatorMetadataRegistry {

    /**
     * 주어진 AAGUID 의 인증기 보안 등급을 반환한다.
     *
     * @param aaguid 인증기 모델 식별자(null/길이 0 이면 미상)
     * @return 보안 등급(미등록 시 {@link AuthenticatorGrade#UNKNOWN})
     */
    AuthenticatorGrade gradeOf(byte[] aaguid);
}
