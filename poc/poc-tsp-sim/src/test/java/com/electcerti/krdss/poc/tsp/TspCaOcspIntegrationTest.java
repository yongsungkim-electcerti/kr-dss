package com.electcerti.krdss.poc.tsp;

import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPReqBuilder;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.SingleResp;
import org.bouncycastle.cert.ocsp.jcajce.JcaCertificateID;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 가상 인정사업자 CA/RA/OCSP E2E — 등록(RA)→발급(CA)→상태질의(OCSP good)→폐지→OCSP revoked.
 *
 * <p>실제 HTTP 표면과 joint-ca.p12 로드 배선을 함께 검증한다.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TspCaOcspIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Test
    void enroll_then_ocsp_good_then_revoke_then_ocsp_revoked() throws Exception {
        // 1) RA 등록 → CA 발급(서버 키 생성)
        @SuppressWarnings("unchecked")
        Map<String, Object> enroll = rest.postForObject(
                url("/ra/enroll"), Map.of("cn", "홍길동 실증"), Map.class);
        assertNotNull(enroll);
        String serialHex = (String) enroll.get("serial");
        String certPem = (String) enroll.get("certificatePem");
        assertNotNull(serialHex);
        assertTrue(certPem.contains("BEGIN CERTIFICATE"));

        X509Certificate issuerCert = fetchCaCertificate();
        BigInteger serial = new BigInteger(serialHex, 16);

        // 발급 인증서에 AIA(id-ad-ocsp, 1.3.6.1.5.5.7.1.1) 확장이 삽입되어 검증기가
        // 이 인정사업자의 OCSP 응답부를 자동으로 찾을 수 있어야 한다.
        X509Certificate issued = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(certPem.getBytes(StandardCharsets.UTF_8)));
        assertNotNull(issued.getExtensionValue("1.3.6.1.5.5.7.1.1"),
                "발급 인증서에 AIA(OCSP) 확장이 있어야 한다");
        assertEquals(issuerCert.getSubjectX500Principal(), issued.getIssuerX500Principal(),
                "발급기관은 가상 인정사업자 CA 여야 한다");

        // 2) OCSP 질의 — good (getCertStatus == null)
        SingleResp good = ocspQuery(issuerCert, serial);
        assertNull(good.getCertStatus(), "발급 직후에는 good(=null) 이어야 한다");

        // 3) 폐지
        ResponseEntity<Map> revoke = rest.postForEntity(
                url("/ocsp/admin/revoke?serial=" + serialHex + "&reason=1"), null, Map.class);
        assertEquals(200, revoke.getStatusCode().value());

        // 4) OCSP 질의 — revoked
        SingleResp revoked = ocspQuery(issuerCert, serial);
        RevokedStatus status = assertInstanceOf(RevokedStatus.class, revoked.getCertStatus());
        assertTrue(status.hasRevocationReason());
    }

    @Test
    void unknown_serial_returns_unknown_status() throws Exception {
        X509Certificate issuerCert = fetchCaCertificate();
        SingleResp resp = ocspQuery(issuerCert, BigInteger.valueOf(0xDEADBEEFL));
        // 미발급 일련번호 → good(null)이 아니어야 한다(UnknownStatus)
        assertNotNull(resp.getCertStatus());
    }

    private SingleResp ocspQuery(X509Certificate issuerCert, BigInteger serial) throws Exception {
        CertificateID id = new JcaCertificateID(
                new JcaDigestCalculatorProviderBuilder().build().get(CertificateID.HASH_SHA1),
                issuerCert, serial);
        OCSPReq request = new OCSPReqBuilder().addRequest(id).build();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/ocsp-request"));
        headers.setAccept(java.util.List.of(MediaType.parseMediaType("application/ocsp-response")));
        ResponseEntity<byte[]> resp = rest.postForEntity(
                url("/ocsp"), new HttpEntity<>(request.getEncoded(), headers), byte[].class);
        assertEquals(200, resp.getStatusCode().value());

        BasicOCSPResp basic = (BasicOCSPResp) new OCSPResp(resp.getBody()).getResponseObject();
        return basic.getResponses()[0];
    }

    private X509Certificate fetchCaCertificate() throws Exception {
        String pem = rest.getForObject(url("/ca/certificate"), String.class);
        assertNotNull(pem);
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(
                new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }
}
