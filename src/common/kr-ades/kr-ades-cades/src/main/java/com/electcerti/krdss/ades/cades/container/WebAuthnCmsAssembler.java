package com.electcerti.krdss.ades.cades.container;

import com.electcerti.krdss.ades.cades.KrAdesOids;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.DERTaggedObject;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.x509.Certificate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * 전자서명 객체 생성부 — WebAuthn 로컬 서명(Mode 1) 모사 컨테이너 패키징(특허-A 청구항 5/11).
 *
 * <p>2단계 전략의 <b>1차 모사 컨테이너</b>. CMS SignerInfo 필드와 1:1 대응하도록 설계하여
 * 후속 정식 {@code CMSSignedData} 승격을 용이하게 한다. 어서션 데이터는 오직
 * {@code unsignedAttrs} 에만 저장하여 순환참조를 방지한다(설계서 §4.2/§4.3).</p>
 *
 * <pre>
 * KrWebAuthnSignature ::= SEQUENCE {
 *     version            INTEGER (1),
 *     signedAttrs        OCTET STRING,            -- T2 DER(SET OF Attribute) 원본 바이트 그대로
 *     signatureAlgorithm OBJECT IDENTIFIER,       -- KrAdesOids.WEBAUTHN_ASSERTION_SIG_ALG
 *     signature          OCTET STRING,            -- assertion.response.signature
 *     certificates       SEQUENCE OF Certificate, -- 서명자 인증서(들)
 *     unsignedAttrs  [1] IMPLICIT SET OF Attribute -- WebAuthnAssertionAttr 수납
 * }
 * </pre>
 */
public final class WebAuthnCmsAssembler {

    private static final int UNSIGNED_ATTRS_TAG = 1;

    /** 파싱 결과. */
    public record Parsed(
            byte[] signedAttrsDer,
            String sigAlgOid,
            byte[] signature,
            List<X509Certificate> certificates,
            WebAuthnAssertionAttr assertion) {
    }

    /**
     * 모사 컨테이너를 조립한다.
     *
     * @param signedAttrsDer      T2 {@code SignedAttrsBuilder.SignedAttrs.der()} 원본 바이트(재인코딩 금지)
     * @param assertionSignature  WebAuthn 어서션 서명값(assertion.response.signature)
     * @param certs               서명자 인증서(들)
     * @param attr                unsignedAttrs 에 수납할 WebAuthn 어서션 데이터
     */
    public byte[] assemble(byte[] signedAttrsDer, byte[] assertionSignature,
                           List<X509Certificate> certs, WebAuthnAssertionAttr attr) {
        if (signedAttrsDer == null || signedAttrsDer.length == 0) {
            throw new IllegalArgumentException("signedAttrsDer 가 비어 있습니다");
        }
        if (assertionSignature == null || assertionSignature.length == 0) {
            throw new IllegalArgumentException("assertionSignature 가 비어 있습니다");
        }
        if (attr == null) {
            throw new IllegalArgumentException("WebAuthnAssertionAttr 가 필요합니다");
        }
        try {
            ASN1EncodableVector v = new ASN1EncodableVector();
            v.add(new ASN1Integer(1));                                   // version
            v.add(new DEROctetString(signedAttrsDer));                  // signedAttrs (원본 바이트 그대로)
            v.add(new ASN1ObjectIdentifier(KrAdesOids.WEBAUTHN_ASSERTION_SIG_ALG)); // signatureAlgorithm
            v.add(new DEROctetString(assertionSignature));             // signature

            // certificates SEQUENCE OF Certificate
            ASN1EncodableVector certVec = new ASN1EncodableVector();
            if (certs != null) {
                for (X509Certificate c : certs) {
                    try {
                        certVec.add(Certificate.getInstance(c.getEncoded()));
                    } catch (java.security.cert.CertificateEncodingException e) {
                        throw new IllegalStateException("인증서 인코딩 실패", e);
                    }
                }
            }
            v.add(new DERSequence(certVec));

            // unsignedAttrs [1] IMPLICIT SET OF Attribute — WebAuthnAssertionAttr 수납
            Attribute attribute = new Attribute(
                    new ASN1ObjectIdentifier(KrAdesOids.WEBAUTHN_ASSERTION_ATTR),
                    new DERSet(attr.toAsn1()));
            ASN1Set unsignedAttrs = new DERSet(attribute);
            v.add(new DERTaggedObject(false, UNSIGNED_ATTRS_TAG, unsignedAttrs));

            return new DERSequence(v).getEncoded(ASN1Encoding.DER);
        } catch (IOException e) {
            throw new IllegalStateException("KrWebAuthnSignature 컨테이너 인코딩 실패", e);
        }
    }

    /** 모사 컨테이너를 파싱한다. */
    public Parsed parse(byte[] container) {
        ASN1Sequence seq;
        try {
            seq = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(container));
        } catch (IOException e) {
            throw new IllegalArgumentException("컨테이너 DER 파싱 실패", e);
        }
        if (seq.size() != 6) {
            throw new IllegalArgumentException("KrWebAuthnSignature 필드 개수 오류: " + seq.size());
        }
        int version = ASN1Integer.getInstance(seq.getObjectAt(0)).intValueExact();
        if (version != 1) {
            throw new IllegalArgumentException("KrWebAuthnSignature version 은 1 이어야 합니다: " + version);
        }
        byte[] signedAttrsDer = ASN1OctetString.getInstance(seq.getObjectAt(1)).getOctets();
        String sigAlgOid = ASN1ObjectIdentifier.getInstance(seq.getObjectAt(2)).getId();
        byte[] signature = ASN1OctetString.getInstance(seq.getObjectAt(3)).getOctets();

        // certificates
        ASN1Sequence certSeq = ASN1Sequence.getInstance(seq.getObjectAt(4));
        List<X509Certificate> certs = new ArrayList<>();
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            for (int i = 0; i < certSeq.size(); i++) {
                byte[] certDer = Certificate.getInstance(certSeq.getObjectAt(i)).getEncoded();
                certs.add((X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certDer)));
            }
        } catch (CertificateException | IOException e) {
            throw new IllegalArgumentException("인증서 복원 실패", e);
        }

        // unsignedAttrs [1] IMPLICIT SET OF Attribute
        ASN1TaggedObject tagged = ASN1TaggedObject.getInstance(seq.getObjectAt(5));
        if (tagged.getTagNo() != UNSIGNED_ATTRS_TAG) {
            throw new IllegalArgumentException("unsignedAttrs 태그 오류: [" + tagged.getTagNo() + "]");
        }
        ASN1Set unsignedAttrs = ASN1Set.getInstance(tagged, false);
        WebAuthnAssertionAttr assertion = extractAssertion(unsignedAttrs);
        if (assertion == null) {
            throw new IllegalArgumentException("unsignedAttrs 에 WebAuthnAssertionAttr 가 없습니다");
        }
        return new Parsed(signedAttrsDer, sigAlgOid, signature, certs, assertion);
    }

    private WebAuthnAssertionAttr extractAssertion(ASN1Set unsignedAttrs) {
        for (int i = 0; i < unsignedAttrs.size(); i++) {
            Attribute attribute = Attribute.getInstance(unsignedAttrs.getObjectAt(i));
            if (KrAdesOids.WEBAUTHN_ASSERTION_ATTR.equals(attribute.getAttrType().getId())) {
                ASN1Set values = attribute.getAttrValues();
                if (values.size() == 0) {
                    throw new IllegalArgumentException("WebAuthnAssertionAttr attrValues 가 비어 있습니다");
                }
                ASN1Sequence attrSeq = ASN1Sequence.getInstance(values.getObjectAt(0));
                return WebAuthnAssertionAttr.fromAsn1(attrSeq);
            }
        }
        return null;
    }
}
