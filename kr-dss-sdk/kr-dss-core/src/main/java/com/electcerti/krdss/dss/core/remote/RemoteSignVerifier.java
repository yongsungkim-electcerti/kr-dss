package com.electcerti.krdss.dss.core.remote;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * 원격전자서명 서명객체 검증기.
 *
 * <p>{@link RemoteSignCoordinator}가 만든 KR-AdES 서명객체(데모 JSON 컨테이너)를 검증한다.
 * 외부 의존 없이 JDK 표준 암호(RSA/PKCS#1)로 서명값을 실제 검증하므로, 검증 결과는
 * 모사가 아닌 실제 암호 검증에 근거한다.</p>
 *
 * <p>검증 항목: ① 서명객체 파싱 ② 인증서 유효기간 ③ 서명값(인증서 공개키로 PKCS#1
 * 검증) ④ (원문 제공 시) 다이제스트 일치. EN 319 102 검증 절차의 핵심 점검을 단순화해
 * 보여준다.</p>
 */
public class RemoteSignVerifier {

    /** SHA-256 DigestInfo 접두부(RFC 8017). */
    private static final byte[] SHA256_DIGEST_INFO_PREFIX = {
            0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48, 0x01,
            0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20
    };

    private final ObjectMapper mapper = new ObjectMapper();

    /** 개별 점검 결과. */
    public record Check(String name, boolean passed, String message) {
    }

    /**
     * 검증 보고서.
     *
     * @param indication   TOTAL_PASSED / TOTAL_FAILED / INDETERMINATE
     * @param subIndication 상세 사유(실패/확인불가 시)
     */
    public record Report(
            String indication,
            String subIndication,
            String signerSubject,
            String signingTime,
            String format,
            String level,
            String certNotBefore,
            String certNotAfter,
            List<Check> checks
    ) {
    }

    /** 원문 없이 서명객체만으로 검증한다. */
    public Report verify(byte[] signedDocument) {
        return verify(signedDocument, null);
    }

    /**
     * 서명객체를 검증한다. 원문이 제공되면 다이제스트 일치까지 확인한다.
     *
     * @param signedDocument   KR-AdES 서명객체(JSON)
     * @param originalDocument 원문(선택) — 무결성(다이제스트) 비교용
     */
    public Report verify(byte[] signedDocument, byte[] originalDocument) {
        List<Check> checks = new ArrayList<>();
        JsonNode root;
        try {
            root = mapper.readTree(signedDocument);
            checks.add(new Check("서명객체 파싱", true, "KR-AdES 서명객체 구조 확인"));
        } catch (Exception e) {
            checks.add(new Check("서명객체 파싱", false, "파싱 실패: " + e.getMessage()));
            return fail("FORMAT_FAILURE", null, null, null, null, null, null, checks);
        }

        String format = root.path("format").asText(null);
        String level = root.path("level").asText(null);
        String signingTime = root.path("signingTime").asText(null);
        String digestAlg = root.path("documentInfo").path("digestAlgorithm").asText("SHA-256");
        byte[] embeddedDigest = decode(root.path("documentInfo").path("digest").asText(null));
        byte[] signatureValue = decode(root.path("signatureValue").asText(null));
        byte[] certDer = decode(root.path("signingCertificate").asText(null));

        if (embeddedDigest == null || signatureValue == null || certDer == null) {
            checks.add(new Check("필수 필드", false, "digest/signatureValue/signingCertificate 누락"));
            return fail("FORMAT_FAILURE", null, signingTime, format, level, null, null, checks);
        }

        // 인증서 로드 + 유효기간
        X509Certificate cert;
        String subject, notBefore, notAfter;
        boolean certValid;
        try {
            cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(certDer));
            subject = cert.getSubjectX500Principal().getName();
            notBefore = cert.getNotBefore().toInstant().toString();
            notAfter = cert.getNotAfter().toInstant().toString();
            Instant now = Instant.now();
            certValid = !now.isBefore(cert.getNotBefore().toInstant()) && !now.isAfter(cert.getNotAfter().toInstant());
            checks.add(new Check("인증서 유효기간", certValid,
                    certValid ? "유효기간 내" : "유효기간 외(만료 또는 미발효)"));
        } catch (Exception e) {
            checks.add(new Check("인증서 파싱", false, "실패: " + e.getMessage()));
            return fail("NO_CERTIFICATE_CHAIN_FOUND", null, signingTime, format, level, null, null, checks);
        }

        // 서명값 검증 — 인증서 공개키로 PKCS#1 서명 복호 후 DigestInfo 비교
        boolean signatureOk;
        try {
            byte[] expected = digestInfo(embeddedDigest);
            byte[] recovered = rsaRecover(cert.getPublicKey(), signatureValue);
            signatureOk = Arrays.equals(expected, recovered);
            checks.add(new Check("서명값 검증", signatureOk,
                    signatureOk ? "인증서 공개키로 서명 검증 성공" : "서명값이 인증서·다이제스트와 불일치"));
        } catch (Exception e) {
            signatureOk = false;
            checks.add(new Check("서명값 검증", false, "검증 오류: " + e.getMessage()));
        }

        // 무결성 — 원문 제공 시 다이제스트 재계산 비교
        boolean digestOk = true;
        if (originalDocument != null) {
            try {
                byte[] recomputed = MessageDigest.getInstance(digestAlg).digest(originalDocument);
                digestOk = Arrays.equals(recomputed, embeddedDigest);
                checks.add(new Check("문서 무결성", digestOk,
                        digestOk ? "원문 다이제스트 일치" : "원문 다이제스트 불일치(문서 변조)"));
            } catch (Exception e) {
                digestOk = false;
                checks.add(new Check("문서 무결성", false, "재계산 실패: " + e.getMessage()));
            }
        }

        String indication, subIndication;
        if (signatureOk && digestOk && certValid) {
            indication = "TOTAL_PASSED";
            subIndication = null;
        } else if (!signatureOk || !digestOk) {
            indication = "TOTAL_FAILED";
            subIndication = !signatureOk ? "SIG_CRYPTO_FAILURE" : "HASH_FAILURE";
        } else {
            indication = "INDETERMINATE";
            subIndication = "OUT_OF_BOUNDS_NOT_REVOKED";  // 인증서 유효기간 외
        }
        return new Report(indication, subIndication, subject, signingTime, format, level, notBefore, notAfter, checks);
    }

    private byte[] rsaRecover(PublicKey publicKey, byte[] signature) throws Exception {
        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.DECRYPT_MODE, publicKey);
        return cipher.doFinal(signature);
    }

    private byte[] digestInfo(byte[] digest) {
        byte[] out = new byte[SHA256_DIGEST_INFO_PREFIX.length + digest.length];
        System.arraycopy(SHA256_DIGEST_INFO_PREFIX, 0, out, 0, SHA256_DIGEST_INFO_PREFIX.length);
        System.arraycopy(digest, 0, out, SHA256_DIGEST_INFO_PREFIX.length, digest.length);
        return out;
    }

    private byte[] decode(String b64) {
        return b64 == null ? null : Base64.getDecoder().decode(b64);
    }

    private Report fail(String sub, String subj, String time, String fmt, String lvl,
                        String nb, String na, List<Check> checks) {
        return new Report("TOTAL_FAILED", sub, subj, time, fmt, lvl, nb, na, checks);
    }
}
