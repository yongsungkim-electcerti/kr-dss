package com.electcerti.krdss.poc.tsp.ca;

import com.electcerti.krdss.poc.tsp.pki.Pem;
import com.electcerti.krdss.poc.tsp.pki.TspCaService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 가상 인정사업자 CA 엔드포인트 — CA 인증서·체인 공개 및 발급 대장 조회.
 */
@RestController
@RequestMapping("/ca")
public class CaController {

    private final TspCaService ca;

    public CaController(TspCaService ca) {
        this.ca = ca;
    }

    /** 발급 CA(가상 인정사업자) 인증서(PEM). */
    @GetMapping(value = "/certificate", produces = MediaType.TEXT_PLAIN_VALUE)
    public String certificate() {
        return Pem.certificate(ca.caCertificate());
    }

    /** CA 인증서 체인(PEM, 발급 CA → 상위 CA). */
    @GetMapping(value = "/chain", produces = MediaType.TEXT_PLAIN_VALUE)
    public String chain() {
        return Pem.chain(ca.caChain());
    }

    /** 발급 대장 요약(일련번호 hex · CN · 상태 · 만료). */
    @GetMapping(value = "/certs", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Map<String, Object>> certs() {
        return ca.list().stream().map(e -> Map.<String, Object>of(
                "serial", e.serial().toString(16),
                "subject", e.subjectCn(),
                "status", e.status().name(),
                "notAfter", e.certificate().getNotAfter().toInstant().toString()
        )).toList();
    }
}
