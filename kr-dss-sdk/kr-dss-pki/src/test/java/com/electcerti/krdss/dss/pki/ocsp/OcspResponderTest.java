package com.electcerti.krdss.dss.pki.ocsp;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.bouncycastle.cert.ocsp.jcajce.JcaCertificateID;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OcspResponder} 단위 테스트 — RFC 6960 응답 조립·서명·상태 매핑을 검증한다.
 *
 * <p>BouncyCastle 규약상 {@code SingleResp.getCertStatus()}는 <b>good일 때 null</b>,
 * revoked/unknown일 때 각 상태 객체를 반환한다.</p>
 */
class OcspResponderTest {

    private X509Certificate caCert;
    private KeyPair caKeyPair;
    private final DigestCalculatorProvider digest;

    OcspResponderTest() throws Exception {
        this.digest = new JcaDigestCalculatorProviderBuilder().build();
    }

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        this.caKeyPair = kpg.generateKeyPair();
        this.caCert = selfSignedCa(caKeyPair);
    }

    @Test
    void good_serial_returns_null_certStatus_and_valid_signature() throws Exception {
        BigInteger serial = BigInteger.valueOf(1001);
        OcspResponder responder = responderFor(s -> OcspCertStatus.good());

        BasicOCSPResp basic = respond(responder, serial);

        assertTrue(basic.isSignatureValid(
                new JcaContentVerifierProviderBuilder().build(caCert.getPublicKey())),
                "OCSP 응답 서명은 CA 공개키로 검증되어야 한다");
        SingleResp single = basic.getResponses()[0];
        assertNull(single.getCertStatus(), "good 상태는 null 이어야 한다(RFC 6960/BC 규약)");
    }

    @Test
    void revoked_serial_returns_RevokedStatus_with_reason() throws Exception {
        BigInteger serial = BigInteger.valueOf(1002);
        Instant revokedAt = Instant.now().minusSeconds(60);
        OcspResponder responder = responderFor(
                s -> OcspCertStatus.revoked(revokedAt, CRLReason.keyCompromise));

        SingleResp single = respond(responder, serial).getResponses()[0];

        RevokedStatus revoked = assertInstanceOf(RevokedStatus.class, single.getCertStatus());
        assertTrue(revoked.hasRevocationReason());
        assertEquals(CRLReason.keyCompromise, revoked.getRevocationReason());
    }

    @Test
    void suspended_serial_maps_to_certificateHold() throws Exception {
        BigInteger serial = BigInteger.valueOf(1003);
        OcspResponder responder = responderFor(s -> OcspCertStatus.suspended(Instant.now()));

        SingleResp single = respond(responder, serial).getResponses()[0];

        RevokedStatus revoked = assertInstanceOf(RevokedStatus.class, single.getCertStatus());
        assertEquals(CRLReason.certificateHold, revoked.getRevocationReason());
    }

    @Test
    void unknown_serial_returns_UnknownStatus() throws Exception {
        OcspResponder responder = responderFor(s -> OcspCertStatus.unknown());

        SingleResp single = respond(responder, BigInteger.valueOf(9999)).getResponses()[0];

        assertInstanceOf(UnknownStatus.class, single.getCertStatus());
    }

    @Test
    void malformed_request_returns_malformed_response() throws Exception {
        OcspResponder responder = responderFor(s -> OcspCertStatus.good());

        OCSPResp resp = new OCSPResp(responder.respond(new byte[] {1, 2, 3, 4}));

        assertEquals(OCSPRespBuilder.MALFORMED_REQUEST, resp.getStatus());
    }

    // --- helpers ---

    private OcspResponder responderFor(OcspStatusSource source) {
        return new OcspResponder(caCert, caCert, caKeyPair.getPrivate(), List.of(caCert), source);
    }

    private BasicOCSPResp respond(OcspResponder responder, BigInteger serial) throws Exception {
        CertificateID id = new JcaCertificateID(digest.get(CertificateID.HASH_SHA1), caCert, serial);
        OCSPReq request = new OCSPReqBuilder().addRequest(id).build();
        OCSPResp resp = new OCSPResp(responder.respond(request.getEncoded()));
        assertEquals(OCSPRespBuilder.SUCCESSFUL, resp.getStatus());
        return (BasicOCSPResp) resp.getResponseObject();
    }

    private static X509Certificate selfSignedCa(KeyPair kp) throws Exception {
        X500Name name = new X500Name("CN=Test Virtual TSP CA");
        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, BigInteger.ONE,
                Date.from(now.minus(Duration.ofHours(1))),
                Date.from(now.plus(Duration.ofDays(3650))),
                name, kp.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(kp.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }
}
