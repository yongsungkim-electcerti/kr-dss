package com.electcerti.krdss.dss.trust.hsm;

import com.electcerti.krdss.dss.api.VerificationStatus;
import com.electcerti.krdss.dss.trust.DeviceTrustList;
import com.electcerti.krdss.dss.trust.HsmTrustEntry;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;

import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * HSM Attestation 검증부 — 특허-C 청구항 2·13·15 (발명 C-2).
 *
 * <p>검증 절차:</p>
 * <ol>
 *   <li>hsmDeviceId 로 KR-TL HSM 서브목록 조회 → Attestation Root Certificate 취득(청구항 13·15).</li>
 *   <li>장치 인증서 → (중간 CA) → Root CA 체인 검증(Root 는 KR-TL 신뢰 앵커).</li>
 *   <li>장치(leaf) 인증서 공개키로 attestationSig 검증.</li>
 *   <li>keyGenStatement.nonExtractable = TRUE 확인(발명 C-2 핵심).</li>
 *   <li>securityLevel 정책 요구 등급 충족 확인.</li>
 *   <li>timestamp 유효성 확인.</li>
 * </ol>
 * <p>결과는 TOTAL_PASSED / INDETERMINATE / TOTAL_FAILED 로 산출한다.</p>
 */
public final class HsmAttestationVerifier {

    private final DeviceTrustList deviceList;
    private final HsmAttestationPolicy policy;

    public HsmAttestationVerifier(DeviceTrustList deviceList, HsmAttestationPolicy policy) {
        this.deviceList = Objects.requireNonNull(deviceList, "deviceList");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** 검증 결과. */
    public record Result(VerificationStatus status, List<String> reasons) {
    }

    /**
     * @param obj         HSM Attestation Object
     * @param deviceChain 장치 인증서 체인 [leaf(장치 Attestation 인증서), (중간 CA)…] — Root 는 KR-TL 에서 조회
     */
    public Result verify(HsmAttestationObject obj, List<X509Certificate> deviceChain) {
        List<String> reasons = new ArrayList<>();

        // 1) hsmDeviceId → KR-TL HSM 서브목록 조회 (청구항 13)
        var entry = deviceList.findHsm(obj.hsmDeviceId());
        if (entry.isEmpty()) {
            reasons.add("HSM 미등재(hsmDeviceId)");
            return new Result(VerificationStatus.TOTAL_FAILED, reasons);
        }
        HsmTrustEntry hsm = entry.get();
        if (hsm.status() != ServiceStatus.GRANTED) {
            reasons.add("HSM 비활성(" + hsm.status() + ")");
            return new Result(VerificationStatus.TOTAL_FAILED, reasons);
        }
        X509Certificate root = hsm.attestationRootCertificate();
        if (root == null) {
            reasons.add("HSM Attestation Root 인증서 미등재 — 검증 불가");
            return new Result(VerificationStatus.INDETERMINATE, reasons);
        }
        if (deviceChain == null || deviceChain.isEmpty()) {
            reasons.add("장치 인증서 체인 없음");
            return new Result(VerificationStatus.TOTAL_FAILED, reasons);
        }

        // 2) Root CA → (중간 CA) → 장치 인증서 체인 검증 (청구항 15)
        if (!verifyChain(deviceChain, root, reasons)) {
            return new Result(VerificationStatus.TOTAL_FAILED, reasons);
        }

        // 3) 장치 인증서 공개키로 attestationSig 검증
        X509Certificate leaf = deviceChain.get(0);
        if (!verifySignature(HsmAttestationCodec.tbsDer(obj), obj.attestationSig(), leaf)) {
            reasons.add("attestationSig 검증 실패");
            return new Result(VerificationStatus.TOTAL_FAILED, reasons);
        }
        reasons.add("Attestation 서명·체인 검증 성공");

        // 4) 비추출성 (발명 C-2 핵심)
        if (policy.requireNonExtractable() && !obj.keyGenStatement().nonExtractable()) {
            reasons.add("서명키 추출 가능(nonExtractable=false) → 무효");
            return new Result(VerificationStatus.TOTAL_FAILED, reasons);
        }

        // 5) 보안 등급 정책
        if (!policy.acceptsSecurityLevel(obj.securityLevel())) {
            reasons.add("보안 등급 미충족(cc=" + obj.securityLevel().ccLevel() + ")");
            return new Result(VerificationStatus.INDETERMINATE, reasons);
        }

        // 6) timestamp 유효성
        Instant now = Instant.now();
        if (obj.timestamp().isAfter(now.plusSeconds(300))) {
            reasons.add("Attestation 시각이 미래");
            return new Result(VerificationStatus.INDETERMINATE, reasons);
        }
        if (obj.timestamp().isBefore(now.minus(policy.maxAge()))) {
            reasons.add("Attestation 시각 만료(경과 " + policy.maxAge() + " 초과)");
            return new Result(VerificationStatus.INDETERMINATE, reasons);
        }

        reasons.add("nonExtractable=TRUE·등급·시각 확인 완료");
        return new Result(VerificationStatus.TOTAL_PASSED, reasons);
    }

    /** 체인의 각 인증서가 상위로 서명되고, 최상위가 Root 로 서명되었는지 검증(+유효기간). */
    private static boolean verifyChain(List<X509Certificate> chain, X509Certificate root, List<String> reasons) {
        try {
            Instant now = Instant.now();
            for (X509Certificate c : chain) {
                c.checkValidity(java.util.Date.from(now));
            }
            for (int i = 0; i < chain.size() - 1; i++) {
                chain.get(i).verify(chain.get(i + 1).getPublicKey());
            }
            chain.get(chain.size() - 1).verify(root.getPublicKey());
            root.checkValidity(java.util.Date.from(now));
            return true;
        } catch (Exception e) {
            reasons.add("체인 검증 실패: " + e.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean verifySignature(byte[] tbs, byte[] sig, X509Certificate leaf) {
        try {
            String alg = leaf.getPublicKey().getAlgorithm().startsWith("EC")
                    ? "SHA256withECDSA" : "SHA256withRSA";
            Signature s = Signature.getInstance(alg);
            s.initVerify(leaf.getPublicKey());
            s.update(tbs);
            return s.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }
}
