package com.electcerti.krdss.dss.trust.hsm;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 특허-C 청구항 9 — HSMAttestationObject DER 라운드트립.
 */
class HsmAttestationCodecTest {

    private static final byte[] DEVICE_ID = {10, 20, 30, 40};
    private static final byte[] INSTANCE_ID = {1, 2, 3};

    @Test
    void der_roundtrip_preserves_fields() throws Exception {
        KeyPair attested = HsmTestSupport.ec();
        HsmAttestationObject o = new HsmAttestationObject(1, DEVICE_ID, INSTANCE_ID, attested.getPublic(),
                new HsmKeyGenStatement("EC", 256, true, "digitalSignature"),
                HsmSecurityLevel.of("EAL4+", "FIPS-140-3-L3"),
                Instant.parse("2026-06-01T00:00:00Z"), new byte[]{9, 8, 7});

        HsmAttestationObject d = HsmAttestationCodec.decode(HsmAttestationCodec.encode(o));

        assertThat(d.version()).isEqualTo(1);
        assertThat(d.hsmDeviceId()).isEqualTo(DEVICE_ID);
        assertThat(d.hsmInstanceId()).isEqualTo(INSTANCE_ID);
        assertThat(d.keyGenStatement()).isEqualTo(o.keyGenStatement());
        assertThat(d.securityLevel()).isEqualTo(o.securityLevel());
        assertThat(d.hsmPublicKey().getEncoded()).isEqualTo(attested.getPublic().getEncoded());
        assertThat(d.timestamp().getEpochSecond()).isEqualTo(o.timestamp().getEpochSecond());
        assertThat(d.attestationSig()).isEqualTo(new byte[]{9, 8, 7});
        // 서명 대상(TBS)이 안정적으로 재구성되어야 함(서명 검증의 전제)
        assertThat(HsmAttestationCodec.tbsDer(d)).isEqualTo(HsmAttestationCodec.tbsDer(o));
    }
}
