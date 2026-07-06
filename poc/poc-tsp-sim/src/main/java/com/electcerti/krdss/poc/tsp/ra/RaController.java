package com.electcerti.krdss.poc.tsp.ra;

import com.electcerti.krdss.poc.tsp.pki.Pem;
import com.electcerti.krdss.poc.tsp.pki.TspCaService;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.io.StringReader;
import java.security.cert.X509Certificate;
import java.util.Map;

/**
 * 가상 인정사업자 RA(등록 기관) 엔드포인트 — 신원확인 후 CA 발급을 위임한다.
 *
 * <p>본 PoC는 신원확인을 스텁 처리(요청 수락 = 확인 성공)하고 CA 발급으로 연결한다.
 * 두 가지 등록 방식을 제공한다:</p>
 * <ul>
 *   <li>{@code POST /ra/enroll} — 서버가 가입자 키쌍을 생성(데모 편의). 인증서+개인키 반환.</li>
 *   <li>{@code POST /ra/enroll-csr} — 가입자가 PKCS#10 CSR 제출(표준). 인증서만 반환.</li>
 * </ul>
 */
@RestController
@RequestMapping("/ra")
public class RaController {

    private static final Logger log = LoggerFactory.getLogger(RaController.class);

    private final TspCaService ca;

    public RaController(TspCaService ca) {
        this.ca = ca;
    }

    /** 등록 요청(신원확인 대상 CN). */
    public record EnrollRequest(String cn) {
    }

    /** 서버 키 생성 등록 — 인증서·개인키·체인(PEM) 반환. */
    @PostMapping(value = "/enroll", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> enroll(@RequestBody EnrollRequest request) {
        if (request == null || request.cn() == null || request.cn().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cn 필수");
        }
        // 신원확인(스텁): 요청 수락 = 확인 성공. 실제 인정사업자는 신원확인 근거를 요구한다.
        log.info("[RA] 신원확인·등록 요청: cn={} (RA={})", request.cn(), ca.ra().raId());
        TspCaService.Enrolled result = ca.enroll(request.cn());
        return Map.of(
                "serial", result.serial().toString(16),
                "subject", request.cn(),
                "issuer", ca.caCertificate().getSubjectX500Principal().getName(),
                "certificatePem", Pem.certificate(result.certificate()),
                "privateKeyPem", Pem.privateKey(result.privateKey()),
                "chainPem", Pem.chain(ca.caChain())
        );
    }

    /** CSR 기반 등록 — 제출한 PKCS#10(PEM)으로 발급, 인증서(PEM) 반환. */
    @PostMapping(value = "/enroll-csr", consumes = {MediaType.TEXT_PLAIN_VALUE, "application/pkcs10"},
            produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> enrollCsr(@RequestBody String csrPem) {
        JcaPKCS10CertificationRequest csr = parseCsr(csrPem);
        log.info("[RA] CSR 등록 요청: subject={}", csr.getSubject());
        try {
            X509Certificate cert = ca.issueFromCsr(csr);
            return Map.of(
                    "serial", cert.getSerialNumber().toString(16),
                    "subject", cert.getSubjectX500Principal().getName(),
                    "issuer", cert.getIssuerX500Principal().getName(),
                    "certificatePem", Pem.certificate(cert),
                    "chainPem", Pem.chain(ca.caChain())
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private static JcaPKCS10CertificationRequest parseCsr(String csrPem) {
        if (csrPem == null || csrPem.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSR(PEM) 본문 필수");
        }
        try (PemReader reader = new PemReader(new StringReader(csrPem))) {
            PemObject obj = reader.readPemObject();
            if (obj == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PEM 형식의 CSR이 아님");
            }
            return new JcaPKCS10CertificationRequest(obj.getContent());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSR 파싱 실패: " + e.getMessage());
        }
    }
}
