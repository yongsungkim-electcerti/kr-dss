package com.electcerti.krdss.dss.core.remote;

/**
 * 원격 서명연산 추상화(콜백).
 *
 * <p>코어는 서명 대상 <b>다이제스트</b>만 콜백에 넘기고, 실제 서명연산이 어디서
 * 어떻게 일어나는지(RSSP/SAM/HSM, CSC 전송 등)는 알지 않는다. 이용사(SIC) 계층이
 * 이 인터페이스를 구현하여 원격 QSCD 와의 통신·서명활성화데이터(SAD) 구성을 담당한다.</p>
 *
 * <p>콜백이 다이제스트를 전달받는 지점에서 SAD 의 <b>해시 바인딩</b>을 구성할 수 있어,
 * 서명 대상과 활성화 승인이 동일한 해시로 묶이는 것을 보장한다.</p>
 */
@FunctionalInterface
public interface RemoteSigner {

    /**
     * 다이제스트에 대한 원격 서명을 수행한다.
     *
     * @param digest          서명 대상 다이제스트
     * @param digestAlgorithm 다이제스트 알고리즘 (예: SHA-256)
     * @return 서명값 + 서명 인증서
     */
    RemoteSignature signDigest(byte[] digest, String digestAlgorithm);
}
