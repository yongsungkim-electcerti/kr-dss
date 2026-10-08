package com.electcerti.krdss.ades.cades.bind;

import com.electcerti.krdss.ades.cades.KrAdesOids;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Set;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.Time;
import org.bouncycastle.asn1.ess.ESSCertIDv2;
import org.bouncycastle.asn1.ess.SigningCertificateV2;

import java.io.IOException;
import java.time.Instant;

/**
 * 서명 결속부 — SignedAttrs(DER) 파서.
 *
 * <p>검증 라우터(특허-A §5.3)가 결속된 3요소를 꺼내 쓰기 위한 파서. {@link SignedAttrsBuilder}가
 * 만든 {@code DER(SET OF Attribute)}에서 messageDigest·signingTime·signingCertificateV2 해시를
 * 추출한다. challenge 재파생은 원본 바이트로 별도 수행하므로 본 파서는 결속 검증(문서 무결성·
 * 서명자 결속)에만 쓴다.</p>
 */
public final class SignedAttrsParser {

    private SignedAttrsParser() {
    }

    /**
     * 파싱 결과.
     *
     * @param messageDigest    H(문서)
     * @param signingTime      서명 시각(없으면 null)
     * @param signingCertHash  서명자 인증서 해시(ESSCertIDv2, 없으면 null)
     */
    public record Parsed(byte[] messageDigest, Instant signingTime, byte[] signingCertHash) {
    }

    /** SignedAttrs DER 을 파싱한다. */
    public static Parsed parse(byte[] signedAttrsDer) {
        ASN1Set set;
        try {
            set = ASN1Set.getInstance(ASN1Primitive.fromByteArray(signedAttrsDer));
        } catch (IOException e) {
            throw new IllegalArgumentException("SignedAttrs DER 파싱 실패", e);
        }
        byte[] messageDigest = null;
        Instant signingTime = null;
        byte[] signingCertHash = null;

        for (int i = 0; i < set.size(); i++) {
            Attribute attr = Attribute.getInstance(set.getObjectAt(i));
            String oid = attr.getAttrType().getId();
            if (attr.getAttrValues().size() == 0) {
                continue;
            }
            var value = attr.getAttrValues().getObjectAt(0);
            switch (oid) {
                case KrAdesOids.MESSAGE_DIGEST ->
                        messageDigest = ASN1OctetString.getInstance(value).getOctets();
                case KrAdesOids.SIGNING_TIME ->
                        signingTime = Time.getInstance(value).getDate().toInstant();
                case KrAdesOids.SIGNING_CERTIFICATE_V2 -> {
                    SigningCertificateV2 scv2 = SigningCertificateV2.getInstance(value);
                    ESSCertIDv2[] certs = scv2.getCerts();
                    if (certs != null && certs.length > 0) {
                        signingCertHash = certs[0].getCertHash();
                    }
                }
                default -> {
                    // contentType 등 기타 속성은 무시
                }
            }
        }
        return new Parsed(messageDigest, signingTime, signingCertHash);
    }
}
