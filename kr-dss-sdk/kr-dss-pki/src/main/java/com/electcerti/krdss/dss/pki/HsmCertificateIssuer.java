package com.electcerti.krdss.dss.pki;

import com.electcerti.krdss.ades.cades.KrAdesOids;
import org.bouncycastle.operator.ContentVerifierProvider;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * HSM 원격 서명 인증서 발급부 — 특허-B 발명 B-4 (청구항 10·15).
 *
 * <p>CSR(PKCS#10)과 HSM Attestation Object 를 함께 수신하여:</p>
 * <ol>
 *   <li>CSR 서명(소유 증명, Proof-of-Possession)을 검증하고,</li>
 *   <li>HSM Attestation 을 검증하여 서명키가 HSM 내에서 생성·보호됨을 확인한 후(청구항 10),</li>
 *   <li>CSR 의 공개키로 X.509 인증서를 발급한다. {@code certificatePolicies} 에는 HSM 검증 경로
 *       정책({@link KrAdesOids#POLICY_HSM}), HSM 보안 등급 정책(청구항 15), RA 식별 정책을 부여한다.</li>
 * </ol>
 */
public final class HsmCertificateIssuer {

    private static final Duration DEFAULT_VALIDITY = Duration.ofDays(365);

    private final CertificateAuthority ca;
    private final HsmAttestationVerifier attestationVerifier;

    public HsmCertificateIssuer(CertificateAuthority ca, HsmAttestationVerifier attestationVerifier) {
        this.ca = Objects.requireNonNull(ca, "ca");
        this.attestationVerifier = Objects.requireNonNull(attestationVerifier, "attestationVerifier");
    }

    /**
     * CSR + HSM Attestation 으로 HSM 원격 서명 인증서를 발급한다(청구항 10·15).
     *
     * @param csrDer      PKCS#10 CSR(DER)
     * @param attestation HSM Attestation Object
     * @param ra          발급 대상 RA
     * @param subjectCn   서명자 인증서 CN
     * @throws IllegalArgumentException CSR 파싱/소유증명 검증 실패
     * @throws IllegalStateException    HSM Attestation 검증 실패
     */
    public HsmIssuedCertificate issueFromCsr(byte[] csrDer, HsmAttestation attestation,
                                             RegistrationAuthority ra, String subjectCn) {
        PublicKey csrPublicKey = verifyCsrAndExtractPublicKey(csrDer);

        HsmAttestationResult result = attestationVerifier.verify(attestation);
        if (!result.verified()) {
            throw new IllegalStateException("HSM Attestation 검증 실패: 서명키의 HSM 생성·보호 미확인");
        }

        List<String> policies = List.of(
                KrAdesOids.POLICY_HSM,      // HSM 검증 경로(특허-A Mode 2)
                result.grade().policyOid(), // HSM 보안 등급(청구항 15)
                ra.policyOid());            // RA 식별(청구항 2·13)
        X509Certificate cert = ca.issue(
                csrPublicKey, subjectCn, attestation.hsmInstanceId(), policies, DEFAULT_VALIDITY);
        String kid = CertificateAuthority.keyIdentifierHex(csrPublicKey);
        return new HsmIssuedCertificate(cert, ra.raId(), result.grade(), kid, result.nonExtractable());
    }

    private static PublicKey verifyCsrAndExtractPublicKey(byte[] csrDer) {
        try {
            PKCS10CertificationRequest csr = new PKCS10CertificationRequest(csrDer);
            ContentVerifierProvider verifier = new JcaContentVerifierProviderBuilder()
                    .build(csr.getSubjectPublicKeyInfo());
            if (!csr.isSignatureValid(verifier)) {
                throw new IllegalArgumentException("CSR 소유 증명(서명) 검증 실패");
            }
            return new JcaPKCS10CertificationRequest(csr).getPublicKey();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("CSR 파싱/검증 실패", e);
        }
    }
}
