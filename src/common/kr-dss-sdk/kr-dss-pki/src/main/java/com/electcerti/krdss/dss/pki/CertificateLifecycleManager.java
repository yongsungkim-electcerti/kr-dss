package com.electcerti.krdss.dss.pki;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 인증서 생명주기 관리 — 특허-B 발명 B-3 (청구항 3·8·9·14).
 *
 * <p>단일 WebAuthn Credential 과 결속된 복수 X.509 인증서의 생명주기를 인증서 단위(serial)로
 * 관리한다:</p>
 * <ul>
 *   <li><b>갱신(Renewal, 청구항 8)</b>: 갱신 시 AAGUID·Attestation 을 재검증하여 보안 등급
 *       변동 여부를 확인하고, 변동된 등급에 따라 정책 OID 를 재결정한 동일 SubjectPublicKeyInfo
 *       인증서를 발급한다(기존 인증서 폐지는 선택).</li>
 *   <li><b>선택적 정지·폐지(청구항 9)</b>: 특정 RA 인증서만 정지/폐지하며, 동일 Credential 에
 *       기반한 다른 RA 인증서에는 영향을 주지 않는다.</li>
 *   <li><b>재발급(Reissuance)</b>: 인증기 분실·손상 등으로 신규 Credential 로 재등록한 경우,
 *       새 공개키로 인증서를 발급하고 기존 인증서를 폐지한다.</li>
 * </ul>
 *
 * <p>추가 발급(Add-on, 청구항 14)은 {@link MultiRaCertificateService#issueForRa} 가 담당하며,
 * 발급된 인증서를 {@link #track} 으로 본 매니저에 등록해 생명주기를 관리한다.</p>
 */
public final class CertificateLifecycleManager {

    private final RegistrationBindingService binding;
    private final ConcurrentHashMap<BigInteger, Tracked> bySerial = new ConcurrentHashMap<>();

    public CertificateLifecycleManager(RegistrationBindingService binding) {
        this.binding = Objects.requireNonNull(binding, "binding");
    }

    /** 신규 발급 + 생명주기 추적 등록. */
    public IssuedCertificate issue(RegistrationResult reg, RegistrationAuthority ra, String subjectCn) {
        IssuedCertificate issued = binding.bind(reg, ra, subjectCn);
        track(issued);
        return issued;
    }

    /** 외부(예: {@link MultiRaCertificateService})에서 발급된 인증서를 추적 등록한다. */
    public void track(IssuedCertificate issued) {
        bySerial.put(issued.certificate().getSerialNumber(), new Tracked(issued, CertificateStatus.VALID));
    }

    /** 인증서 상태 조회. */
    public CertificateStatus status(BigInteger serial) {
        return require(serial).status;
    }

    /** 추적 중인 모든 인증서 수(테스트·진단용). */
    public List<BigInteger> trackedSerials() {
        return List.copyOf(bySerial.keySet());
    }

    // --- 청구항 9: 선택적 정지·폐지 (타 RA 인증서 무영향) ---

    /** 인증서 정지(유효 상태에서만). */
    public void suspend(BigInteger serial) {
        Tracked t = require(serial);
        if (t.status != CertificateStatus.VALID) {
            throw new IllegalStateException("정지는 유효 상태에서만 가능: " + t.status);
        }
        t.status = CertificateStatus.SUSPENDED;
    }

    /** 정지 해제(정지 상태에서만). */
    public void resume(BigInteger serial) {
        Tracked t = require(serial);
        if (t.status != CertificateStatus.SUSPENDED) {
            throw new IllegalStateException("재개는 정지 상태에서만 가능: " + t.status);
        }
        t.status = CertificateStatus.VALID;
    }

    /** 인증서 폐지(종료 상태). */
    public void revoke(BigInteger serial) {
        require(serial).status = CertificateStatus.REVOKED;
    }

    // --- 청구항 8: 갱신 (Attestation 재검증 → 등급·정책 OID 재결정) ---

    /**
     * 기존 Credential 이 유효한 경우의 갱신. 새 WebAuthn 등록 없이 동일 Credential 공개키로
     * 새 인증서를 발급하되, {@code freshReg} 로 AAGUID·Attestation 을 재검증하여 등급·정책 OID 를
     * 재결정한다(청구항 8). {@code revokeOld=true} 이면 기존 인증서를 폐지한다.
     */
    public RenewalOutcome renew(IssuedCertificate old, RegistrationResult freshReg,
                                RegistrationAuthority ra, String subjectCn, boolean revokeOld) {
        AuthenticatorGrade previous = old.grade();
        IssuedCertificate renewed = binding.bind(freshReg, ra, subjectCn);
        track(renewed);
        if (revokeOld) {
            revoke(old.certificate().getSerialNumber());
        }
        return new RenewalOutcome(renewed, previous, renewed.grade(), revokeOld);
    }

    // --- 재발급 (신규 Credential → 새 공개키, 기존 폐지) ---

    /**
     * 인증기 분실·손상으로 신규 등록한 경우의 재발급. {@code newReg} 의 새 공개키로 인증서를
     * 발급하고 기존 인증서를 폐지한다. 새 공개키이므로 SubjectKeyIdentifier 도 달라진다.
     */
    public IssuedCertificate reissue(IssuedCertificate old, RegistrationResult newReg,
                                     RegistrationAuthority ra, String subjectCn) {
        IssuedCertificate fresh = binding.bind(newReg, ra, subjectCn);
        track(fresh);
        revoke(old.certificate().getSerialNumber());
        return fresh;
    }

    private Tracked require(BigInteger serial) {
        Tracked t = bySerial.get(serial);
        if (t == null) {
            throw new IllegalArgumentException("미추적 인증서 serial=" + serial);
        }
        return t;
    }

    private static final class Tracked {
        final IssuedCertificate certificate;
        volatile CertificateStatus status;

        Tracked(IssuedCertificate certificate, CertificateStatus status) {
            this.certificate = certificate;
            this.status = status;
        }
    }
}
