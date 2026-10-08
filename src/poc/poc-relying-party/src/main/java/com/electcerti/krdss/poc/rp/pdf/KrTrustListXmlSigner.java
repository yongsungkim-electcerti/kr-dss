package com.electcerti.krdss.poc.rp.pdf;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.signature.XAdESService;
import java.io.ByteArrayOutputStream;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * KR-TL XML(TS 119 612)에 XAdES enveloped 전자서명을 붙인다.
 *
 * <p>EU TL 과 같은 방식이다. 서명이 신뢰목록 문서 안에 {@code ds:Signature} 로 들어가므로
 * 배포 파일 하나로 목록과 서명이 함께 다닌다. 검증도 EU DSS 의 일반 문서 검증 경로를
 * 그대로 타므로, PDF 검증과 같은 트러스트 앵커·검증정책을 쓴다.</p>
 */
@Service
public class KrTrustListXmlSigner {

    /**
     * @param xml     서명 대상 KR-TL XML
     * @param key     신뢰목록 서명자 개인키
     * @param chain   서명자 인증서 체인 {@code [서명자, 발급 CA, …]}
     * @return {@code ds:Signature} 가 삽입된 XML
     */
    public byte[] sign(byte[] xml, PrivateKey key, List<X509Certificate> chain) {
        if (chain == null || chain.isEmpty()) {
            throw new IllegalArgumentException("KR-TL 서명자 인증서 체인이 비어 있습니다.");
        }

        List<CertificateToken> tokens = new ArrayList<>();
        for (X509Certificate certificate : chain) {
            tokens.add(new CertificateToken(certificate));
        }

        XAdESSignatureParameters parameters = new XAdESSignatureParameters();
        parameters.setSignatureLevel(SignatureLevel.XAdES_BASELINE_B);
        // enveloped — 서명이 신뢰목록 문서 안에 들어간다(TS 119 612 §5.7).
        parameters.setSignaturePackaging(SignaturePackaging.ENVELOPED);
        parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
        parameters.setSigningCertificate(tokens.get(0));
        parameters.setCertificateChain(tokens);

        // 서명 생성 단계에서는 신뢰검증을 하지 않는다(검증 단계의 책임).
        CommonCertificateVerifier certificateVerifier = new CommonCertificateVerifier();
        certificateVerifier.setCheckRevocationForUntrustedChains(false);
        XAdESService service = new XAdESService(certificateVerifier);

        DSSDocument document = new InMemoryDocument(xml, "kr-tl.xml");
        SignatureAlgorithm algorithm = resolveSignatureAlgorithm(key);
        // getDataToSign 과 signDocument 는 같은 parameters 인스턴스를 공유해야 한다.
        ToBeSigned dataToSign = service.getDataToSign(document, parameters);
        SignatureValue signatureValue =
                new SignatureValue(algorithm, rawSign(algorithm, key, dataToSign.getBytes()));
        return toByteArray(service.signDocument(document, parameters, signatureValue));
    }

    private static SignatureAlgorithm resolveSignatureAlgorithm(PrivateKey key) {
        String algorithm = key.getAlgorithm();
        if ("RSA".equalsIgnoreCase(algorithm)) {
            return SignatureAlgorithm.RSA_SHA256;
        }
        if ("EC".equalsIgnoreCase(algorithm) || "ECDSA".equalsIgnoreCase(algorithm)) {
            return SignatureAlgorithm.ECDSA_SHA256;
        }
        throw new IllegalArgumentException("지원하지 않는 KR-TL 서명 키 알고리즘입니다: " + algorithm);
    }

    private static byte[] rawSign(SignatureAlgorithm algorithm, PrivateKey key, byte[] dataToSign) {
        try {
            Signature signature = Signature.getInstance(algorithm.getJCEId());
            signature.initSign(key);
            signature.update(dataToSign);
            return signature.sign();
        } catch (Exception e) {
            throw new IllegalStateException("KR-TL XML 서명값 생성 실패: " + e.getMessage(), e);
        }
    }

    private static byte[] toByteArray(DSSDocument document) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.writeTo(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("서명된 KR-TL XML 직렬화 실패", e);
        }
    }
}
