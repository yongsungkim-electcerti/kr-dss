package com.electcerti.krdss.dss.pki;

import com.electcerti.krdss.ades.cades.KrAdesOids;

import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Registration Binding 서비스 — 특허-B 청구항 1(인증서 결속부 + 장치 등급 판정부).
 *
 * <p>WebAuthn 등록 결과(Registration Result)를 X.509 인증서와 결속한다:</p>
 * <ol>
 *   <li><b>장치 등급 판정부</b>: Attestation 검증 결과 + AAGUID 메타데이터 조회로 보안 등급 판정
 *       (청구항 1·11·12). attestation 미검증 시 {@link AuthenticatorGrade#UNKNOWN}.</li>
 *   <li><b>인증서 결속부</b>: Credential 공개키를 SubjectPublicKeyInfo 로 사용(청구항 5)하고,
 *       검증 경로 정책(특허-A {@link KrAdesOids#POLICY_WEBAUTHN}) · 장치 등급 정책(청구항 11) ·
 *       RA 식별 정책(청구항 2)을 certificatePolicies 에 부여하여 발급.</li>
 * </ol>
 */
public final class RegistrationBindingService {

    private static final Duration DEFAULT_VALIDITY = Duration.ofDays(365);

    private final CertificateAuthority ca;
    private final AuthenticatorMetadataRegistry metadata;

    public RegistrationBindingService(CertificateAuthority ca, AuthenticatorMetadataRegistry metadata) {
        this.ca = Objects.requireNonNull(ca, "ca");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
    }

    /**
     * 장치 보안 등급을 판정한다(청구항 1 장치 등급 판정부, 11·12).
     * attestation 이 검증되지 않으면 AAGUID 와 무관하게 {@link AuthenticatorGrade#UNKNOWN}.
     */
    public AuthenticatorGrade judgeGrade(RegistrationResult reg) {
        if (!reg.attestation().verified()) {
            return AuthenticatorGrade.UNKNOWN;
        }
        return metadata.gradeOf(reg.aaguid());
    }

    /**
     * 단일 RA 에 대한 Registration Binding 을 수행하고 인증서를 발급한다(청구항 1·5·11).
     *
     * @param reg       WebAuthn 등록 결과
     * @param ra        발급 대상 RA
     * @param subjectCn 서명자 인증서 CN
     */
    public IssuedCertificate bind(RegistrationResult reg, RegistrationAuthority ra, String subjectCn) {
        AuthenticatorGrade grade = judgeGrade(reg);
        List<String> policies = List.of(
                KrAdesOids.POLICY_WEBAUTHN, // 검증 경로 식별(특허-A)
                grade.policyOid(),          // 장치 보안 등급(청구항 11)
                ra.policyOid());            // RA 식별(청구항 2·13)
        X509Certificate cert = ca.issue(
                reg.credentialPublicKey(), subjectCn, reg.credentialId(), policies, DEFAULT_VALIDITY);
        String kid = CertificateAuthority.keyIdentifierHex(reg.credentialPublicKey());
        return new IssuedCertificate(cert, ra.raId(), grade, kid);
    }
}
