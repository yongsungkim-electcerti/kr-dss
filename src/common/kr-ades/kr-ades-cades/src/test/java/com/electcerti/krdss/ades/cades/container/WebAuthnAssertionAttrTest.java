package com.electcerti.krdss.ades.cades.container;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 특허-A T4 — WebAuthnAssertionAttr ASN.1 라운드트립/태그 모호성 회귀 검증.
 */
class WebAuthnAssertionAttrTest {

    private static final byte[] AUTH_DATA = "authenticator-data".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CLIENT_DATA =
            "{\"type\":\"webauthn.get\"}".getBytes(StandardCharsets.UTF_8);
    private static final byte[] CRED_ID = "credential-id-bytes".getBytes(StandardCharsets.UTF_8);
    private static final byte[] AAGUID = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    @Test
    void roundtrip_both_optionals_present() {
        var attr = WebAuthnAssertionAttr.of(AUTH_DATA, CLIENT_DATA, -7, CRED_ID, AAGUID);
        var back = WebAuthnAssertionAttr.fromDer(attr.toDer());
        assertThat(back).isEqualTo(attr);
        assertThat(back.credentialId()).containsExactly(CRED_ID);
        assertThat(back.aaguid()).containsExactly(AAGUID);
        assertThat(back.coseAlg()).isEqualTo(-7);
    }

    @Test
    void roundtrip_credentialId_only() {
        var attr = WebAuthnAssertionAttr.of(AUTH_DATA, CLIENT_DATA, -257, CRED_ID, null);
        var back = WebAuthnAssertionAttr.fromDer(attr.toDer());
        assertThat(back).isEqualTo(attr);
        // [0] 단독 → credentialId 로 정확히 복원, aaguid 는 null
        assertThat(back.credentialId()).containsExactly(CRED_ID);
        assertThat(back.aaguid()).isNull();
    }

    @Test
    void roundtrip_aaguid_only() {
        var attr = WebAuthnAssertionAttr.of(AUTH_DATA, CLIENT_DATA, -7, null, AAGUID);
        var back = WebAuthnAssertionAttr.fromDer(attr.toDer());
        assertThat(back).isEqualTo(attr);
        // [1] 단독 → aaguid 로 정확히 복원, credentialId 는 null (모호성 회귀 방지)
        assertThat(back.aaguid()).containsExactly(AAGUID);
        assertThat(back.credentialId()).isNull();
    }

    @Test
    void roundtrip_no_optionals() {
        var attr = WebAuthnAssertionAttr.of(AUTH_DATA, CLIENT_DATA, -7, null, null);
        var back = WebAuthnAssertionAttr.fromDer(attr.toDer());
        assertThat(back).isEqualTo(attr);
        assertThat(back.credentialId()).isNull();
        assertThat(back.aaguid()).isNull();
    }

    @Test
    void rejects_invalid_version() {
        assertThatThrownBy(() -> new WebAuthnAssertionAttr(2, AUTH_DATA, CLIENT_DATA, -7, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_missing_required_fields() {
        assertThatThrownBy(() -> WebAuthnAssertionAttr.of(null, CLIENT_DATA, -7, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebAuthnAssertionAttr.of(AUTH_DATA, new byte[0], -7, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void defensive_copy_isolated_from_caller() {
        byte[] mutable = CRED_ID.clone();
        var attr = WebAuthnAssertionAttr.of(AUTH_DATA, CLIENT_DATA, -7, mutable, null);
        mutable[0] ^= 0xff;                          // 외부 변경
        assertThat(attr.credentialId()).containsExactly(CRED_ID);  // 영향 없음
    }
}
