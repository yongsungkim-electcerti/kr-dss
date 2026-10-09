package com.electcerti.krdss.poc.kisa.web;

import com.electcerti.krdss.poc.kisa.issuance.Issuance;
import com.electcerti.krdss.poc.kisa.issuance.IssuanceService;
import java.time.Instant;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PoC 관리 화면의 TL 발행 엔드포인트. 공개 제공(IF-07)과 분리한다.
 * 오류 응답 형식은 {@link RegistryAdminController}의 예외 처리기를 공유한다.
 */
@RestController
@RequestMapping("/api/admin/issuance")
public class IssuanceAdminController {

    private final IssuanceService service;

    public IssuanceAdminController(IssuanceService service) {
        this.service = service;
    }

    /** 권장값·사전 검사. 저장하지 않는다. */
    @GetMapping("/plan")
    public Issuance.Plan plan(@RequestParam(required = false) Issuance.Purpose purpose,
            @RequestParam(required = false) String sequenceNumber,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant issuedAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant nextUpdate) {
        return service.plan(purpose, sequenceNumber, issuedAt, nextUpdate);
    }

    @PostMapping("/issuances")
    public Issuance.Result issue(@RequestBody Issuance.Request request) {
        return service.issue(request);
    }

    @GetMapping("/issuances")
    public List<Issuance.Entry> list() {
        return service.list();
    }

    @GetMapping("/issuances/{issuanceId}")
    public Issuance.Manifest manifest(@PathVariable String issuanceId) {
        return service.manifest(issuanceId);
    }

    /** 저장된 서명 XML 원문 그대로. 재직렬화·재서명하지 않는다. */
    @GetMapping("/issuances/{issuanceId}/signed.xml")
    public ResponseEntity<byte[]> signedXml(@PathVariable String issuanceId) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + issuanceId + ".xml\"")
                .body(service.signedXml(issuanceId));
    }
}
