package com.electcerti.krdss.ades.core;

/**
 * 포맷별 서명 프로파일 어댑터의 공통 계약.
 *
 * <p>각 포맷 모듈(kr-ades-xades 등)이 본 인터페이스를 구현하여 SDK 핵심 서비스가
 * 포맷에 독립적으로 서명·추출을 수행하도록 한다.</p>
 */
public interface SignatureProfileAdapter {

    /** 이 어댑터가 담당하는 포맷. */
    KrAdesFormat format();

    /** 주어진 레벨/패키징 조합을 지원하는지 여부. */
    boolean supports(KrAdesLevel level, PackagingType packaging);
}
