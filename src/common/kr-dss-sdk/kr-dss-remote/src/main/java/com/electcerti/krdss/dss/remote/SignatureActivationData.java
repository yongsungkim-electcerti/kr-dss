package com.electcerti.krdss.dss.remote;

import java.util.List;

/**
 * 서명활성화데이터 (SAD, Signature Activation Data) — 페이로드 모델.
 *
 * <p>EN 419 241-1 의 핵심 개념으로, 서명자가 <b>해당 서명에 대해 단독 통제권
 * (sole control)</b> 을 행사했음을 증빙한다. <b>SAM 이 서명자 인증(2FA) 성공 후
 * 발급</b>하며, {@link Jws} 로 HMAC 서명되어 위변조를 탐지할 수 있는 토큰 형태로
 * 전달된다. 본 레코드는 그 토큰의 페이로드에 해당한다.</p>
 *
 * <ul>
 *   <li>{@code credentialID} — 활성화 대상 자격증명</li>
 *   <li>{@code hashes} — 이 SAD 로 서명을 허가하는 다이제스트 목록(해시 바인딩)</li>
 *   <li>{@code signerId} — 인증에 성공한 서명자 식별자</li>
 *   <li>{@code nonce} — 1회용 식별자(재사용 방지)</li>
 *   <li>{@code issuedAtEpochSec} / {@code expiresAtEpochSec} — 발급·만료 시각</li>
 * </ul>
 *
 * @see Jws
 */
public record SignatureActivationData(
        String credentialID,
        List<String> hashes,
        String signerId,
        String nonce,
        long issuedAtEpochSec,
        long expiresAtEpochSec
) {
}
