package com.electcerti.krdss.poc.rp.pdf;

import com.electcerti.krdss.ades.core.KrAdesLevel;
import com.electcerti.krdss.ades.core.PackagingType;
import com.electcerti.krdss.ades.pades.PAdesProfileAdapter;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * PDF Baseline 전자서명 — PAdES-BASELINE-B (ETSI EN 319 142).
 *
 * <p>Whale PoC 범위상 TSA·OCSP 를 쓰지 않으므로 레벨은 B 로 고정한다. 시점보증(-T)이나
 * 장기검증(-LT/-LTA)은 각각 TSA·폐지정보를 필요로 해 이 경로에서는 만들지 않는다.</p>
 */
@Service
public class PdfBaselineSigner {

    private static final Logger log = LoggerFactory.getLogger(PdfBaselineSigner.class);

    private final PAdesProfileAdapter profileAdapter = new PAdesProfileAdapter();

    /**
     * 업로드된 PDF 를 업로드된 인증서(PKCS#12)로 서명한다.
     *
     * @param pdf      원본 PDF
     * @param pdfName  원본 파일명(서명본 이름 생성에 사용)
     * @param keyStore 서명용 PKCS#12 바이트
     * @param password PKCS#12 비밀번호
     * @param reason   서명 사유(PDF 서명 딕셔너리 /Reason)
     * @param location 서명 장소(/Location)
     */
    public SignedPdf sign(byte[] pdf, String pdfName, byte[] keyStore, char[] password,
                          String reason, String location) {
        // PAdES 는 PDF 내장 서명이라 ENVELOPED 만 의미가 있다. 어댑터에 실제로 물어본다.
        if (!profileAdapter.supports(KrAdesLevel.KR_B, PackagingType.ENVELOPED)) {
            throw new IllegalStateException("KR-PAdES 어댑터가 KR-B/ENVELOPED 를 지원하지 않는다");
        }

        KeyEntry entry = firstKeyEntry(keyStore, password);
        SignatureAlgorithm signatureAlgorithm = resolveSignatureAlgorithm(entry.privateKey());

        PAdESSignatureParameters parameters = new PAdESSignatureParameters();
        parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);
        parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
        parameters.setSigningCertificate(entry.signingCertificate());
        parameters.setCertificateChain(entry.chain());
        if (reason != null && !reason.isBlank()) {
            parameters.setReason(reason);
        }
        if (location != null && !location.isBlank()) {
            parameters.setLocation(location);
        }

        // 서명 단계에서는 신뢰검증을 하지 않는다(검증 단계의 책임). 폐지 조회도 끈다.
        CommonCertificateVerifier certificateVerifier = new CommonCertificateVerifier();
        certificateVerifier.setCheckRevocationForUntrustedChains(false);
        PAdESService padesService = new PAdESService(certificateVerifier);

        DSSDocument document = new InMemoryDocument(pdf, pdfName != null ? pdfName : "document.pdf");
        // getDataToSign 과 signDocument 는 같은 parameters 인스턴스를 써야 한다.
        // PAdES 는 서명 시각·문서 개정 정보를 parameters 에 담아 두 호출 사이에 공유한다.
        ToBeSigned dataToSign = padesService.getDataToSign(document, parameters);
        SignatureValue signatureValue =
                new SignatureValue(signatureAlgorithm, rawSign(signatureAlgorithm, entry.privateKey(), dataToSign.getBytes()));
        DSSDocument signed = padesService.signDocument(document, parameters, signatureValue);

        String signerSubject = entry.signingCertificate().getCertificate().getSubjectX500Principal().getName();
        log.info("[PDF] PAdES-BASELINE-B 서명 완료 — signer={}, alg={}", signerSubject, signatureAlgorithm.getJCEId());
        return new SignedPdf(toByteArray(signed), signedName(pdfName), signerSubject,
                signatureAlgorithm.getJCEId(), SignatureLevel.PAdES_BASELINE_B.name());
    }

    /** 키스토어에서 개인키를 가진 첫 별칭을 고른다. */
    private static KeyEntry firstKeyEntry(byte[] keyStoreBytes, char[] password) {
        KeyStore keyStore;
        try {
            keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(keyStoreBytes), password);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "인증서 파일(PKCS#12)을 열지 못했습니다. 파일 형식과 비밀번호를 확인하세요: " + e.getMessage(), e);
        }
        try {
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (!keyStore.isKeyEntry(alias)) {
                    continue;
                }
                PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password);
                Certificate[] chain = keyStore.getCertificateChain(alias);
                if (privateKey == null || chain == null || chain.length == 0) {
                    continue;
                }
                List<CertificateToken> tokens = new ArrayList<>();
                for (Certificate certificate : chain) {
                    tokens.add(new CertificateToken((X509Certificate) certificate));
                }
                return new KeyEntry(privateKey, tokens.get(0), tokens);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("인증서에서 개인키를 꺼내지 못했습니다: " + e.getMessage(), e);
        }
        throw new IllegalArgumentException("인증서 파일에 개인키 항목이 없습니다.");
    }

    private static SignatureAlgorithm resolveSignatureAlgorithm(PrivateKey privateKey) {
        String algorithm = privateKey.getAlgorithm();
        if ("RSA".equalsIgnoreCase(algorithm)) {
            return SignatureAlgorithm.RSA_SHA256;
        }
        if ("EC".equalsIgnoreCase(algorithm) || "ECDSA".equalsIgnoreCase(algorithm)) {
            return SignatureAlgorithm.ECDSA_SHA256;
        }
        throw new IllegalArgumentException("지원하지 않는 서명 키 알고리즘입니다: " + algorithm);
    }

    private static byte[] rawSign(SignatureAlgorithm algorithm, PrivateKey privateKey, byte[] dataToSign) {
        try {
            Signature signature = Signature.getInstance(algorithm.getJCEId());
            signature.initSign(privateKey);
            signature.update(dataToSign);
            return signature.sign();
        } catch (Exception e) {
            throw new IllegalStateException("서명값 생성 실패: " + e.getMessage(), e);
        }
    }

    private static String signedName(String original) {
        String base = (original == null || original.isBlank()) ? "document.pdf" : original;
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        return stem + "-signed.pdf";
    }

    private static byte[] toByteArray(DSSDocument document) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.writeTo(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("서명 문서 직렬화 실패", e);
        }
    }

    private record KeyEntry(PrivateKey privateKey, CertificateToken signingCertificate, List<CertificateToken> chain) {
    }

    /** 서명 결과. */
    public record SignedPdf(byte[] bytes, String fileName, String signerSubject, String signatureAlgorithm,
                            String signatureLevel) {
    }
}
