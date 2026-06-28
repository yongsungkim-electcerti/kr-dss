package com.electcerti.krdss.ades.cades.bind;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

/**
 * 서명 결속부(Signature Binding Module) — 서명 대상 속성(SignedAttrs) 구성.
 *
 * <p>특허-A 청구항 1/4: 전자문서·서명 시각·서명자 인증서 3요소를 CMS SignedAttributes
 * 로 묶는다. 이후 {@link SignatureBindingService}가 그 인코딩값의 해시로 WebAuthn
 * Challenge 를 파생함으로써 3요소를 단일 Challenge 에 암호학적으로 결속한다.</p>
 *
 * <ul>
 *   <li>contentType        — eContentType(기본 id-data)</li>
 *   <li>messageDigest      — H(전자문서) (문서 결속)</li>
 *   <li>signingTime        — 서명 시각 (replay 방지, 시간 결속)</li>
 *   <li>signingCertificateV2 — H(서명자 인증서) (서명자 결속, RFC 5035)</li>
 * </ul>
 */
public final class SignedAttrsBuilder {

    private SignedAttrsBuilder() {
    }

    /** 결속 결과 — SignedAttrs DER 와 구성에 사용된 메타. */
    public record SignedAttrs(byte[] der, byte[] messageDigest, Instant signingTime, HashSuite hashSuite) {
    }

    /**
     * 3요소를 결속한 SignedAttrs 를 구성한다.
     *
     * @param documentDigest 전자문서 다이제스트 H(문서) — {@code hashSuite}로 계산된 값
     * @param signingTime    서명 시각
     * @param signerCert     서명자 인증서
     * @param hashSuite      messageDigest/인증서 해시에 사용할 알고리즘(암호 민첩성)
     */
    public static SignedAttrs build(byte[] documentDigest, Instant signingTime,
                                    X509Certificate signerCert, HashSuite hashSuite) {
        if (documentDigest == null || documentDigest.length == 0) {
            throw new IllegalArgumentException("documentDigest 가 비어 있습니다");
        }
        if (signerCert == null) {
            throw new IllegalArgumentException("signerCert 가 필요합니다");
        }
        try {
            ASN1EncodableVector v = new ASN1EncodableVector();

            // contentType = id-data
            v.add(new Attribute(CMSAttributes.contentType,
                    new DERSet(new ASN1ObjectIdentifier(
                            com.electcerti.krdss.ades.cades.KrAdesOids.ID_DATA))));

            // messageDigest = H(문서)
            v.add(new Attribute(CMSAttributes.messageDigest,
                    new DERSet(new DEROctetString(documentDigest))));

            // signingTime
            v.add(new Attribute(CMSAttributes.signingTime,
                    new DERSet(new Time(Date.from(signingTime)))));

            // signingCertificateV2 = ESSCertIDv2(H(인증서)) — 기본 해시 SHA-256
            byte[] certHash = hashSuite.digest(signerCert.getEncoded());
            ESSCertIDv2 essCertId = new ESSCertIDv2(certHash);
            SigningCertificateV2 scv2 = new SigningCertificateV2(essCertId);
            v.add(new Attribute(
                    new ASN1ObjectIdentifier(com.electcerti.krdss.ades.cades.KrAdesOids.SIGNING_CERTIFICATE_V2),
                    new DERSet(scv2)));

            byte[] der = new DERSet(v).getEncoded(ASN1Encoding.DER);
            return new SignedAttrs(der, documentDigest.clone(), signingTime, hashSuite);
        } catch (IOException | java.security.cert.CertificateEncodingException e) {
            throw new IllegalStateException("SignedAttrs 인코딩 실패", e);
        }
    }
}
