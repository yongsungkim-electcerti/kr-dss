package com.electcerti.krdss.poc.kisa.web;

import com.electcerti.krdss.poc.kisa.registry.RegistryDraft;
import com.electcerti.krdss.poc.kisa.registry.RegistryDraft.CertificateRecord;
import com.electcerti.krdss.poc.kisa.registry.RegistryException;
import com.electcerti.krdss.poc.kisa.registry.RegistryService;
import com.electcerti.krdss.poc.kisa.registry.RegistryService.ProviderInput;
import com.electcerti.krdss.poc.kisa.registry.RegistryService.ServiceInfoInput;
import com.electcerti.krdss.poc.kisa.registry.RegistryService.ServiceInput;
import com.electcerti.krdss.poc.kisa.registry.RegistryService.StatusChange;
import com.electcerti.krdss.poc.kisa.registry.RegistryStore;
import com.electcerti.krdss.tl.model.PocTrustListProfile.ServiceType;
import com.electcerti.krdss.tl.model.PocTrustListProfile.TrustPointLevel;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * PoC 관리자 화면 전용 엔드포인트. 외부 사업자 연계 인터페이스가 아니며 IF-07 공개 조회와 분리한다.
 * 모든 변경 요청은 화면이 마지막으로 읽은 {@code expectedRevision}을 함께 보낸다.
 */
@RestController
@RequestMapping("/api/admin")
public class RegistryAdminController {

    private final RegistryService service;

    public RegistryAdminController(RegistryService service) {
        this.service = service;
    }

    /** 화면 상태: 저장소 가용 여부, 활성 초안, 현황 집계. 저장소를 쓸 수 없으면 draft는 null이다. */
    public record AdminState(RegistryStore.Health health, RegistryDraft draft, RegistryService.Overview overview) {
    }

    public record InspectRequest(String certificate, ServiceType type, TrustPointLevel level) {
    }

    public record InspectedCertificate(CertificateRecord certificate, List<String> warnings) {
    }

    public record CertificatesRequest(List<String> certificates) {
    }

    @GetMapping("/state")
    public AdminState state() {
        var health = service.health();
        if (!health.available()) return new AdminState(health, null, null);
        return new AdminState(health, service.current(), service.overview());
    }

    @GetMapping("/revisions")
    public List<RegistryStore.RevisionSummary> revisions(@RequestParam(defaultValue = "50") int limit) {
        return service.revisions(Math.max(1, Math.min(limit, 200)));
    }

    @GetMapping(value = "/preview.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public byte[] previewXml() {
        return service.previewXml();
    }

    @PostMapping("/certificates/inspect")
    public List<InspectedCertificate> inspect(@RequestBody InspectRequest request) {
        return service.inspect(request.certificate(), request.type(), request.level()).stream()
                .map(i -> new InspectedCertificate(i.record(), i.warnings()))
                .toList();
    }

    @PutMapping("/scheme")
    public RegistryService.Result updateScheme(@RequestParam long expectedRevision,
            @RequestBody RegistryDraft.SchemeSettings settings) {
        return service.updateScheme(expectedRevision, settings);
    }

    @PostMapping("/providers")
    public RegistryService.Result addProvider(@RequestParam long expectedRevision, @RequestBody ProviderInput input) {
        return service.addProvider(expectedRevision, input);
    }

    @PutMapping("/providers/{providerId}")
    public RegistryService.Result updateProvider(@RequestParam long expectedRevision, @PathVariable String providerId,
            @RequestBody ProviderInput input) {
        return service.updateProvider(expectedRevision, providerId, input);
    }

    @DeleteMapping("/providers/{providerId}")
    public RegistryService.Result removeProvider(@RequestParam long expectedRevision,
            @PathVariable String providerId) {
        return service.removeProvider(expectedRevision, providerId);
    }

    @PostMapping("/providers/{providerId}/services")
    public RegistryService.Result addService(@RequestParam long expectedRevision, @PathVariable String providerId,
            @RequestBody ServiceInput input) {
        return service.addService(expectedRevision, providerId, input);
    }

    @PutMapping("/providers/{providerId}/services/{serviceId}")
    public RegistryService.Result updateService(@RequestParam long expectedRevision, @PathVariable String providerId,
            @PathVariable String serviceId, @RequestBody ServiceInfoInput input) {
        return service.updateServiceInfo(expectedRevision, providerId, serviceId, input);
    }

    @PostMapping("/providers/{providerId}/services/{serviceId}/status")
    public RegistryService.Result changeStatus(@RequestParam long expectedRevision, @PathVariable String providerId,
            @PathVariable String serviceId, @RequestBody StatusChange change) {
        return service.changeStatus(expectedRevision, providerId, serviceId, change);
    }

    @DeleteMapping("/providers/{providerId}/services/{serviceId}")
    public RegistryService.Result removeService(@RequestParam long expectedRevision, @PathVariable String providerId,
            @PathVariable String serviceId) {
        return service.removeService(expectedRevision, providerId, serviceId);
    }

    @PostMapping("/providers/{providerId}/services/{serviceId}/certificates")
    public RegistryService.Result addCertificates(@RequestParam long expectedRevision,
            @PathVariable String providerId, @PathVariable String serviceId,
            @RequestBody CertificatesRequest request) {
        return service.addCertificates(expectedRevision, providerId, serviceId, request.certificates());
    }

    @DeleteMapping("/providers/{providerId}/services/{serviceId}/certificates/{sha256}")
    public RegistryService.Result removeCertificate(@RequestParam long expectedRevision,
            @PathVariable String providerId, @PathVariable String serviceId, @PathVariable String sha256) {
        return service.removeCertificate(expectedRevision, providerId, serviceId, sha256);
    }

    /** 인증서 원문 내려받기(DER). */
    @GetMapping(value = "/providers/{providerId}/services/{serviceId}/certificates/{sha256}.cer",
            produces = "application/pkix-cert")
    public byte[] certificate(@PathVariable String providerId, @PathVariable String serviceId,
            @PathVariable String sha256) {
        var draft = service.current();
        return draft.providers().stream().filter(p -> p.providerId().equals(providerId))
                .flatMap(p -> p.services().stream()).filter(s -> s.serviceId().equals(serviceId))
                .flatMap(s -> s.certificates().stream()).filter(c -> c.sha256().equals(sha256))
                .findFirst()
                .map(c -> Base64.getDecoder().decode(c.derBase64()))
                .orElseThrow(() -> new RegistryException(RegistryException.Code.NOT_FOUND,
                        "인증서를 찾을 수 없습니다: " + sha256));
    }

    @ExceptionHandler(RegistryException.class)
    public ResponseEntity<Map<String, String>> registryError(RegistryException e) {
        var status = switch (e.code()) {
            case INVALID_INPUT -> HttpStatus.UNPROCESSABLE_ENTITY;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case REVISION_CONFLICT, IDENTITY_CONFLICT -> HttpStatus.CONFLICT;
            case STATE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case STORAGE_FAILED -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", e.code().name(), "message", String.valueOf(e.getMessage())));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("code", "INVALID_INPUT", "message", "요청 형식이 올바르지 않습니다(값·날짜·선택 항목 확인)."));
    }
}
