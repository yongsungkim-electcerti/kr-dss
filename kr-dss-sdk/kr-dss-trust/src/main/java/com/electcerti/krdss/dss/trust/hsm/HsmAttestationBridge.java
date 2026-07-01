package com.electcerti.krdss.dss.trust.hsm;

import com.electcerti.krdss.dss.api.VerificationStatus;
import com.electcerti.krdss.dss.pki.HsmAttestation;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Objects;

/**
 * 특허-C C-2 ↔ 특허-B B-4 브리지.
 *
 * <p>특허-B {@code HsmCertificateIssuer} 는 HSM Attestation 의 서명 검증 결과를 boolean
 * ({@code signatureVerified})으로 입력받는다(구조는 특허-C 참조). 본 브리지는 특허-C C-2
 * {@link HsmAttestationVerifier}(Root CA 체인·attestationSig·nonExtractable 실검증)로 그 값을
 * 산출하여, 특허-B 발급 인프라에 전달할 {@link HsmAttestation} 을 구성한다. 특허-B 잔여
 * (signatureVerified 스텁)를 실검증으로 대체한다.</p>
 */
public final class HsmAttestationBridge {

    private final HsmAttestationVerifier verifier;

    public HsmAttestationBridge(HsmAttestationVerifier verifier) {
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    /**
     * C-2 로 HSM Attestation Object 를 검증하고, 특허-B 발급용 {@link HsmAttestation} 을 만든다.
     * {@code signatureVerified} 에는 C-2 검증 결과(TOTAL_PASSED 여부)를 반영한다.
     */
    public HsmAttestation verifyAndBuild(HsmAttestationObject obj, List<X509Certificate> deviceChain) {
        HsmAttestationVerifier.Result result = verifier.verify(obj, deviceChain);
        boolean verified = result.status() == VerificationStatus.TOTAL_PASSED;
        return new HsmAttestation(
                obj.hsmDeviceId(),
                obj.hsmInstanceId(),
                obj.keyGenStatement().nonExtractable(),
                obj.securityLevel().ccLevel(),
                verified);
    }
}
