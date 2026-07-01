package com.electcerti.krdss.dss.trust.hsm;

import com.electcerti.krdss.dss.pki.CertificateAuthority;
import com.electcerti.krdss.dss.pki.HsmCertificateIssuer;
import com.electcerti.krdss.dss.pki.HsmGrade;
import com.electcerti.krdss.dss.pki.KrPkiOids;
import com.electcerti.krdss.dss.pki.RegistrationAuthority;
import com.electcerti.krdss.dss.trust.DeviceTrustList;
import com.electcerti.krdss.dss.trust.HsmTrustEntry;
import com.electcerti.krdss.dss.trust.TrustListHsmRegistry;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C-2 → B-4 연계 — 특허-C 실검증 결과가 특허-B HSM 인증서 발급을 좌우(잔여 #7 해소).
 */
class HsmAttestationBridgeTest {

    private static final byte[] DEVICE_ID = {10, 20, 30, 40};

    private DeviceTrustList trustList(java.security.cert.X509Certificate root) {
        return new DeviceTrustList().registerHsm(new HsmTrustEntry(
                DEVICE_ID, "Thales", "Luna7", "EAL4+", "FIPS-140-3-L3",
                root, HsmGrade.HIGH, ServiceStatus.GRANTED));
    }

    private HsmAttestationObject attestation(HsmTestSupport.Pki pki, KeyPair attested) throws Exception {
        return new HsmAttestationGenerator().generate(DEVICE_ID, new byte[]{1},
                attested.getPublic(), new HsmKeyGenStatement("EC", 256, true, "digitalSignature"),
                HsmSecurityLevel.of("EAL4+", "FIPS-140-3-L3"), Instant.now(), pki.deviceKp().getPrivate());
    }

    private static byte[] csr(KeyPair key) throws Exception {
        var builder = new JcaPKCS10CertificationRequestBuilder(new X500Name("CN=HSM Signer"), key.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(key.getPrivate());
        return builder.build(signer).getEncoded();
    }

    @Test
    void valid_c2_attestation_lets_patentB_issue_certificate() throws Exception {
        var pki = HsmTestSupport.pki();
        var devices = trustList(pki.root());
        var bridge = new HsmAttestationBridge(
                new HsmAttestationVerifier(devices, HsmAttestationPolicy.defaults()));
        var issuer = new HsmCertificateIssuer(new CertificateAuthority("ca.kr-dss.example"),
                new com.electcerti.krdss.dss.pki.HsmAttestationVerifier(new TrustListHsmRegistry(devices)));
        var ra = new RegistrationAuthority("RA-HSM", "원격서명기관",
                RegistrationAuthority.RaType.PLATFORM, KrPkiOids.raPolicy("9"));

        KeyPair signingKey = HsmTestSupport.ec();
        var patentBAttestation = bridge.verifyAndBuild(attestation(pki, signingKey), List.of(pki.device()));
        assertThat(patentBAttestation.signatureVerified()).isTrue();

        var issued = issuer.issueFromCsr(csr(signingKey), patentBAttestation, ra, "HSM Signer");
        assertThat(issued.grade()).isEqualTo(HsmGrade.HIGH);
    }

    @Test
    void tampered_c2_attestation_blocks_patentB_issue() throws Exception {
        var pki = HsmTestSupport.pki();
        var devices = trustList(pki.root());
        var bridge = new HsmAttestationBridge(
                new HsmAttestationVerifier(devices, HsmAttestationPolicy.defaults()));
        var issuer = new HsmCertificateIssuer(new CertificateAuthority("ca.kr-dss.example"),
                new com.electcerti.krdss.dss.pki.HsmAttestationVerifier(new TrustListHsmRegistry(devices)));
        var ra = new RegistrationAuthority("RA-HSM", "원격서명기관",
                RegistrationAuthority.RaType.PLATFORM, KrPkiOids.raPolicy("9"));

        KeyPair signingKey = HsmTestSupport.ec();
        HsmAttestationObject obj = attestation(pki, signingKey);
        byte[] sig = obj.attestationSig();
        sig[sig.length - 1] ^= 0x01;
        var patentBAttestation = bridge.verifyAndBuild(obj.withSignature(sig), List.of(pki.device()));

        assertThat(patentBAttestation.signatureVerified()).isFalse();
        // 특허-B 발급기는 미검증 attestation 을 거부
        assertThatThrownBy(() -> issuer.issueFromCsr(csr(signingKey), patentBAttestation, ra, "HSM Signer"))
                .isInstanceOf(IllegalStateException.class);
    }
}
