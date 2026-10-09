package com.electcerti.krdss.tl.builder;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.EncryptionAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.signature.XAdESService;
import java.io.ByteArrayInputStream;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import javax.xml.XMLConstants;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMValidateContext;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * TL XML의 enveloped XAdES-BASELINE-B 서명과 서명 자체 확인.
 *
 * <p>서명 결과 바이트가 발행본의 원문이다. 이후 재직렬화하지 않는다. {@link #check}는 암호 서명과
 * 참조 다이제스트만 확인하며 서명 인증서의 체인·신뢰·TL 내용의 유효성은 판정하지 않는다.</p>
 */
public final class KrTrustListXmlSigner {
    private static final String XADES_NS = "http://uri.etsi.org/01903/v1.3.2#";

    private KrTrustListXmlSigner() {
    }

    /**
     * @param chain 서명자 인증서부터 상위 CA 순서. KeyInfo에 함께 싣는다(신뢰앵커 Root는 제외 권장).
     */
    public static byte[] sign(byte[] unsignedXml, PrivateKey key, List<X509Certificate> chain, Instant signingTime) {
        Objects.requireNonNull(unsignedXml);
        Objects.requireNonNull(key);
        if (chain == null || chain.isEmpty()) throw new IllegalArgumentException("서명자 인증서가 필요합니다.");
        try {
            var parameters = new XAdESSignatureParameters();
            parameters.setSignatureLevel(SignatureLevel.XAdES_BASELINE_B);
            parameters.setSignaturePackaging(SignaturePackaging.ENVELOPED);
            parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
            parameters.setSigningCertificate(new CertificateToken(chain.get(0)));
            parameters.setCertificateChain(chain.stream().map(CertificateToken::new).toList());
            parameters.bLevel().setSigningDate(Date.from(signingTime));

            var service = new XAdESService(new CommonCertificateVerifier());
            var document = new InMemoryDocument(unsignedXml);
            var toBeSigned = service.getDataToSign(document, parameters);
            var encryption = EncryptionAlgorithm.forKey(chain.get(0).getPublicKey());
            var algorithm = SignatureAlgorithm.getAlgorithm(encryption, DigestAlgorithm.SHA256);
            var signer = Signature.getInstance(algorithm.getJCEId());
            signer.initSign(key);
            signer.update(toBeSigned.getBytes());
            var value = new SignatureValue(algorithm, signer.sign());
            return service.signDocument(document, parameters, value).openStream().readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("TL XML 서명에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /** 서명 자체 확인 결과. signer는 KeyInfo의 첫 인증서다. */
    public record SignatureCheck(boolean valid, X509Certificate signer, String detail) {
    }

    /** JDK XMLDSig로 SignatureValue와 모든 Reference 다이제스트를 확인한다. */
    public static SignatureCheck check(byte[] signedXml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(signedXml));
            NodeList signatures = document.getElementsByTagNameNS(XMLSignature.XMLNS, "Signature");
            if (signatures.getLength() != 1) {
                return new SignatureCheck(false, null, "ds:Signature가 정확히 하나여야 합니다: " + signatures.getLength());
            }
            Element signatureElement = (Element) signatures.item(0);
            if (signatureElement.getParentNode() != document.getDocumentElement()
                    || signatureElement.getNextSibling() != null) {
                return new SignatureCheck(false, null, "ds:Signature는 TL 루트의 마지막 자식이어야 합니다.");
            }
            NodeList certificates = signatureElement.getElementsByTagNameNS(XMLSignature.XMLNS, "X509Certificate");
            if (certificates.getLength() == 0) return new SignatureCheck(false, null, "KeyInfo에 서명 인증서가 없습니다.");
            var signer = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(
                    new ByteArrayInputStream(java.util.Base64.getMimeDecoder()
                            .decode(certificates.item(0).getTextContent())));
            NodeList signedProperties = document.getElementsByTagNameNS(XADES_NS, "SignedProperties");
            for (int i = 0; i < signedProperties.getLength(); i++) {
                ((Element) signedProperties.item(i)).setIdAttribute("Id", true);
            }
            var context = new DOMValidateContext(signer.getPublicKey(), signatureElement);
            var signature = XMLSignatureFactory.getInstance("DOM").unmarshalXMLSignature(context);
            boolean valid = signature.validate(context);
            return new SignatureCheck(valid, signer, valid ? "서명값과 참조 다이제스트가 일치합니다."
                    : "서명값 또는 참조 다이제스트가 일치하지 않습니다.");
        } catch (Exception e) {
            return new SignatureCheck(false, null, "서명을 확인할 수 없습니다: " + e.getMessage());
        }
    }
}
