package com.electcerti.krdss.ades.cades.container;

import com.electcerti.krdss.ades.cades.KrAdesOids;
import com.electcerti.krdss.ades.cades.bind.HashSuite;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.IssuerAndSerialNumber;
import org.bouncycastle.asn1.cms.SignedData;
import org.bouncycastle.asn1.cms.SignerIdentifier;
import org.bouncycastle.asn1.cms.SignerInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.Certificate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * 전자서명 객체 생성부 — WebAuthn 로컬 서명(Mode 1) <b>정식 CMS 컨테이너</b>(특허-A 청구항 5/11).
 *
 * <p>2단계 전략의 <b>2차 정식 컨테이너</b>. 모사 {@link WebAuthnCmsAssembler} 와 동일한 결속
 * 의미를 유지하되, 산출물을 RFC 5652 {@code ContentInfo/SignedData} 로 만들어 표준 CMS 도구로
 * 로드 가능하게 한다. 고수준 {@code CMSSignedDataGenerator} 는 ContentSigner 로 서명을 직접
 * 계산하므로 사용할 수 없고(서명값=WebAuthn 어서션, signedAttrs=결속 원본 바이트 보존이 필요),
 * BouncyCastle 저수준 {@link SignerInfo} 빌더로 조립한다(설계서 §4.3).</p>
 *
 * <p>CMS SignerInfo 매핑(설계서 §4.3):</p>
 * <ul>
 *   <li>{@code signerInfos[0].signedAttrs} = T2 SignedAttrs(원본 DER 바이트, {@code [0] IMPLICIT})</li>
 *   <li>{@code signerInfos[0].digestAlgorithm} = {@link HashSuite} OID</li>
 *   <li>{@code signerInfos[0].signatureAlgorithm} = {@link KrAdesOids#WEBAUTHN_ASSERTION_SIG_ALG}(라우팅 힌트)</li>
 *   <li>{@code signerInfos[0].signature} = WebAuthn 어서션 서명(<b>표준 CMS 검증 불가</b> — 라우터 WebAuthn 경로 전용)</li>
 *   <li>{@code signerInfos[0].unsignedAttrs} = {@link WebAuthnAssertionAttr}(순환참조 방지, §4.2)</li>
 *   <li>{@code SignedData.certificates} = 서명자 인증서(들)</li>
 *   <li>{@code SignedData.encapContentInfo} = id-data, eContent 부재(detached)</li>
 * </ul>
 *
 * <p>파싱 결과는 모사 컨테이너와 동일한 {@link WebAuthnCmsAssembler.Parsed} 로 반환하여
 * {@code VerificationRouter} 가 두 포맷을 동일 로직으로 검증하도록 한다.</p>
 */
public final class WebAuthnCmsSignedData {

    /**
     * 정식 CMS({@code ContentInfo/SignedData}) 컨테이너를 조립한다.
     *
     * @param signedAttrsDer     T2 {@code SignedAttrsBuilder.SignedAttrs.der()} 원본 바이트(SET OF, 재인코딩 금지)
     * @param hashSuite          digestAlgorithm 에 기재할 해시 스위트(결속에 사용한 것과 동일)
     * @param assertionSignature WebAuthn 어서션 서명값(assertion.response.signature)
     * @param certs              서명자 인증서(들) — {@code SignedData.certificates}
     * @param attr               unsignedAttrs 에 수납할 WebAuthn 어서션 데이터
     */
    public byte[] assemble(byte[] signedAttrsDer, HashSuite hashSuite, byte[] assertionSignature,
                           List<X509Certificate> certs, WebAuthnAssertionAttr attr) {
        if (signedAttrsDer == null || signedAttrsDer.length == 0) {
            throw new IllegalArgumentException("signedAttrsDer 가 비어 있습니다");
        }
        if (hashSuite == null) {
            throw new IllegalArgumentException("hashSuite 가 필요합니다");
        }
        if (assertionSignature == null || assertionSignature.length == 0) {
            throw new IllegalArgumentException("assertionSignature 가 비어 있습니다");
        }
        if (certs == null || certs.isEmpty()) {
            throw new IllegalArgumentException("서명자 인증서가 최소 1개 필요합니다(SignerIdentifier 구성)");
        }
        if (attr == null) {
            throw new IllegalArgumentException("WebAuthnAssertionAttr 가 필요합니다");
        }
        try {
            X509Certificate signerCert = certs.get(0);
            Certificate signerStruct = Certificate.getInstance(signerCert.getEncoded());

            // signedAttrs: 결속 원본 바이트를 그대로 SET 으로 복원(재인코딩 금지) → SignerInfo 가 [0] IMPLICIT 로 태깅
            ASN1Set signedAttrs = (ASN1Set) ASN1Primitive.fromByteArray(signedAttrsDer);

            AlgorithmIdentifier digestAlg = new AlgorithmIdentifier(new ASN1ObjectIdentifier(hashSuite.oid()));
            AlgorithmIdentifier sigAlg = new AlgorithmIdentifier(
                    new ASN1ObjectIdentifier(KrAdesOids.WEBAUTHN_ASSERTION_SIG_ALG));

            // unsignedAttrs: WebAuthnAssertionAttr
            Attribute assertionAttr = new Attribute(
                    new ASN1ObjectIdentifier(KrAdesOids.WEBAUTHN_ASSERTION_ATTR),
                    new DERSet(attr.toAsn1()));
            ASN1Set unsignedAttrs = new DERSet(assertionAttr);

            SignerIdentifier sid = new SignerIdentifier(new IssuerAndSerialNumber(signerStruct));
            SignerInfo signerInfo = new SignerInfo(
                    sid, digestAlg, signedAttrs, sigAlg,
                    new DEROctetString(assertionSignature), unsignedAttrs);

            // SignedData.certificates
            org.bouncycastle.asn1.ASN1EncodableVector certVec = new org.bouncycastle.asn1.ASN1EncodableVector();
            for (X509Certificate c : certs) {
                certVec.add(Certificate.getInstance(c.getEncoded()));
            }
            ASN1Set certSet = new DERSet(certVec);

            // encapContentInfo = id-data, eContent 부재(detached)
            ContentInfo encapContentInfo = new ContentInfo(CMSObjectIdentifiers.data, null);

            SignedData signedData = new SignedData(
                    new DERSet(digestAlg),     // digestAlgorithms
                    encapContentInfo,          // encapContentInfo
                    certSet,                   // certificates
                    null,                      // crls
                    new DERSet(signerInfo));   // signerInfos

            return new ContentInfo(CMSObjectIdentifiers.signedData, signedData)
                    .getEncoded(ASN1Encoding.DER);
        } catch (IOException | java.security.cert.CertificateEncodingException e) {
            throw new IllegalStateException("정식 CMS SignedData 컨테이너 인코딩 실패", e);
        }
    }

    /**
     * 정식 CMS 컨테이너를 파싱한다. 모사 컨테이너와 동일한 {@link WebAuthnCmsAssembler.Parsed} 로 반환한다.
     *
     * @throws IllegalArgumentException 본 구조가 id-signedData + WebAuthn SignerInfo 가 아닐 때
     *                                  (라우터가 다음 후보 포맷으로 위임하도록 함)
     */
    public WebAuthnCmsAssembler.Parsed parse(byte[] container) {
        ContentInfo contentInfo;
        try {
            contentInfo = ContentInfo.getInstance(ASN1Primitive.fromByteArray(container));
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalArgumentException("CMS ContentInfo DER 파싱 실패", e);
        }
        if (!CMSObjectIdentifiers.signedData.equals(contentInfo.getContentType())) {
            throw new IllegalArgumentException("id-signedData 가 아닙니다: " + contentInfo.getContentType());
        }
        SignedData signedData = SignedData.getInstance(contentInfo.getContent());

        ASN1Set signerInfos = signedData.getSignerInfos();
        if (signerInfos == null || signerInfos.size() != 1) {
            throw new IllegalArgumentException("SignerInfo 는 정확히 1개여야 합니다: "
                    + (signerInfos == null ? 0 : signerInfos.size()));
        }
        SignerInfo signerInfo = SignerInfo.getInstance(signerInfos.getObjectAt(0));

        ASN1Set authAttrs = signerInfo.getAuthenticatedAttributes();
        if (authAttrs == null) {
            throw new IllegalArgumentException("signedAttrs 가 없습니다");
        }
        byte[] signedAttrsDer;
        try {
            // 결속 challenge 입력 바이트(SET OF)를 복원 — DER canonical 이므로 원본과 동일
            signedAttrsDer = authAttrs.getEncoded(ASN1Encoding.DER);
        } catch (IOException e) {
            throw new IllegalArgumentException("signedAttrs 재인코딩 실패", e);
        }

        String sigAlgOid = signerInfo.getDigestEncryptionAlgorithm().getAlgorithm().getId();
        byte[] signature = signerInfo.getEncryptedDigest().getOctets();

        List<X509Certificate> certs = extractCertificates(signedData.getCertificates());

        WebAuthnAssertionAttr assertion = extractAssertion(signerInfo.getUnauthenticatedAttributes());
        if (assertion == null) {
            throw new IllegalArgumentException("unsignedAttrs 에 WebAuthnAssertionAttr 가 없습니다");
        }
        return new WebAuthnCmsAssembler.Parsed(signedAttrsDer, sigAlgOid, signature, certs, assertion);
    }

    private List<X509Certificate> extractCertificates(ASN1Set certSet) {
        List<X509Certificate> certs = new ArrayList<>();
        if (certSet == null) {
            return certs;
        }
        try {
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            for (int i = 0; i < certSet.size(); i++) {
                byte[] certDer = Certificate.getInstance(certSet.getObjectAt(i)).getEncoded();
                certs.add((X509Certificate) cf.generateCertificate(new ByteArrayInputStream(certDer)));
            }
        } catch (CertificateException | IOException e) {
            throw new IllegalArgumentException("인증서 복원 실패", e);
        }
        return certs;
    }

    private WebAuthnAssertionAttr extractAssertion(ASN1Set unsignedAttrs) {
        if (unsignedAttrs == null) {
            return null;
        }
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
