package com.electcerti.krdss.dss.trust.hsm;

import com.electcerti.krdss.dss.api.VerificationStatus;
import com.electcerti.krdss.dss.pki.HsmGrade;
import com.electcerti.krdss.dss.trust.DeviceTrustList;
import com.electcerti.krdss.dss.trust.HsmTrustEntry;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 특허-C 청구항 2·13·15 — HSM Attestation 검증(체인·서명·비추출성·등급).
 */
class HsmAttestationVerifierTest {

    private static final byte[] DEVICE_ID = {10, 20, 30, 40};
    private static final byte[] INSTANCE_ID = {1, 2, 3};

    private final HsmAttestationGenerator generator = new HsmAttestationGenerator();

    /** DEVICE_ID → root 를 등재한 KR-TL HSM 서브목록. */
    private DeviceTrustList trustListWithRoot(X509Certificate root) {
        return new DeviceTrustList().registerHsm(new HsmTrustEntry(
                DEVICE_ID, "Thales", "Luna7", "EAL4+", "FIPS-140-3-L3",
                root, HsmGrade.HIGH, ServiceStatus.GRANTED));
    }

    private HsmAttestationObject attestation(HsmTestSupport.Pki pki, byte[] deviceId,
                                             boolean nonExtractable) throws Exception {
        KeyPair attested = HsmTestSupport.ec();
        return generator.generate(deviceId, INSTANCE_ID, attested.getPublic(),
                new HsmKeyGenStatement("EC", 256, nonExtractable, "digitalSignature"),
                HsmSecurityLevel.of("EAL4+", "FIPS-140-3-L3"), Instant.now(),
                pki.deviceKp().getPrivate());
    }

    @Test
    void valid_attestation_total_passed() throws Exception {
        var pki = HsmTestSupport.pki();
        var verifier = new HsmAttestationVerifier(trustListWithRoot(pki.root()), HsmAttestationPolicy.defaults());

        var result = verifier.verify(attestation(pki, DEVICE_ID, true), List.of(pki.device()));

        assertThat(result.status()).as(result.reasons().toString()).isEqualTo(VerificationStatus.TOTAL_PASSED);
    }

    @Test
    void tampered_signature_total_failed() throws Exception {
        var pki = HsmTestSupport.pki();
        var verifier = new HsmAttestationVerifier(trustListWithRoot(pki.root()), HsmAttestationPolicy.defaults());

        HsmAttestationObject obj = attestation(pki, DEVICE_ID, true);
        byte[] sig = obj.attestationSig();
        sig[sig.length - 1] ^= 0x01;
        HsmAttestationObject tampered = obj.withSignature(sig);

        assertThat(verifier.verify(tampered, List.of(pki.device())).status())
                .isEqualTo(VerificationStatus.TOTAL_FAILED);
    }

    @Test
    void extractable_key_total_failed() throws Exception {
        var pki = HsmTestSupport.pki();
        var verifier = new HsmAttestationVerifier(trustListWithRoot(pki.root()), HsmAttestationPolicy.defaults());

        // nonExtractable=false → 비추출성 미보장 → 무효 (발명 C-2 핵심)
        var result = verifier.verify(attestation(pki, DEVICE_ID, false), List.of(pki.device()));

        assertThat(result.status()).isEqualTo(VerificationStatus.TOTAL_FAILED);
        assertThat(result.reasons()).anyMatch(r -> r.contains("nonExtractable"));
    }

    @Test
    void unregistered_device_total_failed() throws Exception {
        var pki = HsmTestSupport.pki();
        var verifier = new HsmAttestationVerifier(trustListWithRoot(pki.root()), HsmAttestationPolicy.defaults());
        byte[] unknown = {99, 98, 97, 96};

        assertThat(verifier.verify(attestation(pki, unknown, true), List.of(pki.device())).status())
                .isEqualTo(VerificationStatus.TOTAL_FAILED);
    }

    @Test
    void chain_to_wrong_root_total_failed() throws Exception {
        var pki = HsmTestSupport.pki();
        // KR-TL 에는 다른 Root 를 등재 → 장치 인증서 체인이 신뢰 앵커로 이어지지 않음
        var otherRoot = HsmTestSupport.selfSignedCa(HsmTestSupport.ec(), "CN=Other Root,O=X,C=KR");
        var verifier = new HsmAttestationVerifier(trustListWithRoot(otherRoot), HsmAttestationPolicy.defaults());

        assertThat(verifier.verify(attestation(pki, DEVICE_ID, true), List.of(pki.device())).status())
                .isEqualTo(VerificationStatus.TOTAL_FAILED);
    }
}
