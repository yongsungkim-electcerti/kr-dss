package com.electcerti.krdss.poc.rp.pki;

import com.electcerti.krdss.dss.pki.AuthenticatorGrade;
import com.electcerti.krdss.dss.pki.HsmGrade;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 특허-B 데모 API — {@code /api/pki/*}.
 *
 * <p>단일 WebAuthn 등록 결과로 복수 RA 인증서를 발급하고(Registration Binding + Multi-RA),
 * 인증서 생명주기(정지·재개·폐지·갱신)를 제어하며, HSM 원격서명 인증서를 CSR+Attestation 으로
 * 발급한다. 특허-A Mode 1({@code /api/local/*})·Mode 2({@code /api/*})와 분리된 경로.</p>
 */
@RestController
@RequestMapping("/api/pki")
public class PatentBController {

    private final MultiRaRegistrationService service;

    public PatentBController(MultiRaRegistrationService service) {
        this.service = service;
    }

    @GetMapping("/ras")
    public List<Map<String, String>> ras() {
        return service.registrationAuthorities().stream()
                .map(ra -> Map.of("raId", ra.raId(), "name", ra.name(),
                        "type", ra.type().name(), "policyOid", ra.policyOid()))
                .toList();
    }

    // === 단일 등록 → 복수 RA 인증서 ===

    public record RegisterMultiRequest(String publicKey, String credentialId, Integer coseAlg,
                                       String aaguid, Boolean attestationVerified,
                                       String aaguidGrade) {
    }

    @PostMapping("/register-multi")
    public List<MultiRaRegistrationService.CertView> registerMulti(@RequestBody RegisterMultiRequest req) {
        if (req.publicKey() == null || req.credentialId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "publicKey/credentialId 필요");
        }
        try {
            byte[] spki = Base64.getUrlDecoder().decode(req.publicKey());
            byte[] aaguid = req.aaguid() == null ? new byte[16] : Base64.getUrlDecoder().decode(req.aaguid());
            if (req.aaguidGrade() != null && !req.aaguidGrade().isBlank()) {
                service.seedAuthenticatorGrade(aaguid, AuthenticatorGrade.valueOf(req.aaguidGrade().trim()));
            }
            return service.registerMulti(spki, req.credentialId(),
                    req.coseAlg() == null ? -7 : req.coseAlg(), aaguid,
                    req.attestationVerified() == null || req.attestationVerified());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/by-kid/{kid}")
    public List<MultiRaRegistrationService.CertView> byKid(@PathVariable String kid) {
        return service.findByKeyIdentifier(kid);
    }

    // === 생명주기 ===

    public record SerialRequest(String serial) {
    }

    public record RenewRequest(String serial, Boolean attestationVerified) {
    }

    @PostMapping("/lifecycle/suspend")
    public MultiRaRegistrationService.CertView suspend(@RequestBody SerialRequest req) {
        return run(() -> service.suspend(req.serial()));
    }

    @PostMapping("/lifecycle/resume")
    public MultiRaRegistrationService.CertView resume(@RequestBody SerialRequest req) {
        return run(() -> service.resume(req.serial()));
    }

    @PostMapping("/lifecycle/revoke")
    public MultiRaRegistrationService.CertView revoke(@RequestBody SerialRequest req) {
        return run(() -> service.revoke(req.serial()));
    }

    @PostMapping("/lifecycle/renew")
    public MultiRaRegistrationService.CertView renew(@RequestBody RenewRequest req) {
        return run(() -> service.renew(req.serial(),
                req.attestationVerified() == null || req.attestationVerified()));
    }

    @GetMapping("/status/{serial}")
    public Map<String, String> status(@PathVariable String serial) {
        return run(() -> Map.of("serial", serial, "status", service.status(serial).name()));
    }

    // === HSM 발급 ===

    public record HsmIssueRequest(String csr, String hsmDeviceId, String hsmInstanceId,
                                  Boolean nonExtractable, String securityLevel,
                                  Boolean signatureVerified, String raId, String deviceGrade) {
    }

    @PostMapping("/hsm/issue")
    public MultiRaRegistrationService.HsmCertView issueHsm(@RequestBody HsmIssueRequest req) {
        if (req.csr() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "csr(PKCS#10 DER, base64) 필요");
        }
        try {
            byte[] csr = Base64.getDecoder().decode(req.csr());
            byte[] deviceId = req.hsmDeviceId() == null ? new byte[]{0}
                    : Base64.getUrlDecoder().decode(req.hsmDeviceId());
            byte[] instanceId = req.hsmInstanceId() == null ? new byte[0]
                    : Base64.getUrlDecoder().decode(req.hsmInstanceId());
            if (req.deviceGrade() != null && !req.deviceGrade().isBlank()) {
                service.seedHsmDevice(deviceId, HsmGrade.valueOf(req.deviceGrade().trim()));
            }
            return service.issueHsm(csr, deviceId, instanceId,
                    req.nonExtractable() == null || req.nonExtractable(),
                    req.securityLevel() == null ? "EAL4+" : req.securityLevel(),
                    req.signatureVerified() == null || req.signatureVerified(),
                    req.raId() == null ? "RA-BANK" : req.raId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        }
    }

    private <T> T run(java.util.function.Supplier<T> action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }
}
