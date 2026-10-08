package com.electcerti.krdss.dss.pki;

import java.util.Objects;

/**
 * 등록 기관(RA) (특허-B 청구항 2·6·7).
 *
 * <p>RA 는 사용자 신원확인을 수행하고 WebAuthn 등록·서명 절차를 CA 에 위임한다.
 * CA 가 단일 WebAuthn RP(rpId)로 동작하므로, 각 RA 는 자신을 식별하는 고유한
 * 인증서 정책 OID({@code policyOid}, 청구항 13)를 갖는다.</p>
 *
 * @param raId      RA 식별자
 * @param name      표시명(없으면 raId)
 * @param type      RA 유형(청구항 7)
 * @param policyOid 이 RA 를 식별하는 인증서 정책 OID(청구항 2·13)
 */
public record RegistrationAuthority(String raId, String name, RaType type, String policyOid) {

    public RegistrationAuthority {
        Objects.requireNonNull(raId, "raId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(policyOid, "policyOid");
        if (name == null) {
            name = raId;
        }
    }

    /**
     * RA 유형 (특허-B 청구항 7).
     */
    public enum RaType {
        /** 신원확인 기능 + 서비스 플랫폼 보유. */
        IDENTITY,
        /** 자체 인증 플랫폼의 인증 결과를 신원확인 근거로 활용. */
        PLATFORM
    }
}
