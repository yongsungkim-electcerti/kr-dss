package com.electcerti.krdss.poc.tsp.ocsp;

import com.electcerti.krdss.dss.pki.ocsp.OcspResponder;
import com.electcerti.krdss.poc.tsp.pki.TspCaService;
import org.bouncycastle.asn1.x509.CRLReason;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigInteger;
import java.util.Map;

/**
 * 가상 인정사업자 OCSP 응답부 — RFC 6960 표준 요청/응답 + 데모용 폐지 관리 엔드포인트.
 *
 * <ul>
 *   <li>{@code POST /ocsp} — {@code application/ocsp-request}(DER)를 받아 서명된
 *       {@code application/ocsp-response}(DER)를 반환한다. {@code openssl ocsp}·
 *       {@code PKIXRevocationChecker} 등 표준 클라이언트와 상호운용된다.</li>
 *   <li>{@code POST /ocsp/admin/{revoke,suspend,resume}} — 발급 인증서 상태를 변경해
 *       OCSP 응답 결과를 데모로 확인한다(일련번호는 hex).</li>
 * </ul>
 */
@RestController
@RequestMapping("/ocsp")
public class OcspController {

    private static final String OCSP_REQUEST = "application/ocsp-request";
    private static final String OCSP_RESPONSE = "application/ocsp-response";

    private final OcspResponder responder;
    private final TspCaService ca;

    public OcspController(OcspResponder responder, TspCaService ca) {
        this.responder = responder;
        this.ca = ca;
    }

    /** RFC 6960 OCSP 질의 — DER 요청 → 서명된 DER 응답. */
    @PostMapping(consumes = OCSP_REQUEST, produces = OCSP_RESPONSE)
    public ResponseEntity<byte[]> query(@RequestBody byte[] ocspRequest) {
        byte[] der = responder.respond(ocspRequest);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(OCSP_RESPONSE))
                .body(der);
    }

    /** 폐지(사유 코드 기본 unspecified; 예: 1=keyCompromise). */
    @PostMapping(value = "/admin/revoke", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> revoke(@RequestParam("serial") String serialHex,
                                      @RequestParam(value = "reason", defaultValue = "0") int reason) {
        BigInteger serial = parseSerial(serialHex);
        exec(() -> ca.revoke(serial, reason));
        return status(serial, "REVOKED");
    }

    /** 정지(certificateHold). */
    @PostMapping(value = "/admin/suspend", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> suspend(@RequestParam("serial") String serialHex) {
        BigInteger serial = parseSerial(serialHex);
        exec(() -> ca.suspend(serial));
        return status(serial, "SUSPENDED");
    }

    /** 정지 해제. */
    @PostMapping(value = "/admin/resume", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> resume(@RequestParam("serial") String serialHex) {
        BigInteger serial = parseSerial(serialHex);
        exec(() -> ca.resume(serial));
        return status(serial, "VALID");
    }

    /** 현재 상태 조회. */
    @GetMapping(value = "/admin/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> statusOf(@RequestParam("serial") String serialHex) {
        BigInteger serial = parseSerial(serialHex);
        TspCaService.Entry e = ca.find(serial);
        if (e == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "미발급 일련번호: " + serialHex);
        }
        return status(serial, e.status().name());
    }

    private static BigInteger parseSerial(String serialHex) {
        try {
            return new BigInteger(serialHex, 16);
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "일련번호(hex) 형식 오류: " + serialHex);
        }
    }

    private static void exec(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private static Map<String, Object> status(BigInteger serial, String status) {
        return Map.of("serial", serial.toString(16), "status", status);
    }

    /** 사유 코드 참고(문서용): certificateHold = {@value CRLReason#certificateHold}. */
    @SuppressWarnings("unused")
    private static final int HOLD = CRLReason.certificateHold;
}
