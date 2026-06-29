package com.electcerti.krdss.ades.cades.container;

import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.ASN1TaggedObject;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERTaggedObject;

import java.io.IOException;
import java.util.Arrays;

/**
 * 컨테이너 결속 — WebAuthn 어서션 데이터(특허-A 청구항 10).
 *
 * <p>전자서명 객체의 비서명 속성(unsignedAttrs)에 저장되는 어서션 데이터 구조.
 * 순환참조 방지를 위해 절대 SignedAttrs 에 포함하지 않는다(설계서 §4.2).</p>
 *
 * <pre>
 * WebAuthnAssertionAttr ::= SEQUENCE {
 *     version            INTEGER (1),
 *     authenticatorData  OCTET STRING,
 *     clientDataJSON     OCTET STRING,        -- 원문 바이트 그대로 보관
 *     coseAlg            INTEGER,             -- 예: -7(ES256), -257(RS256)
 *     credentialId   [0] IMPLICIT OCTET STRING OPTIONAL,
 *     aaguid         [1] IMPLICIT OCTET STRING OPTIONAL
 * }
 * </pre>
 *
 * <p>동일 UNIVERSAL 타입(OCTET STRING) OPTIONAL 두 개가 연속하므로, DER 디코딩
 * 모호성 제거를 위해 IMPLICIT 컨텍스트 태그({@code [0]}/{@code [1]})를 적용한다.</p>
 */
public record WebAuthnAssertionAttr(
        int version,
        byte[] authenticatorData,
        byte[] clientDataJSON,
        int coseAlg,
        byte[] credentialId,
        byte[] aaguid) {

    private static final int CRED_ID_TAG = 0;
    private static final int AAGUID_TAG = 1;

    /** 방어복사 + 불변식 검증. */
    public WebAuthnAssertionAttr {
        if (version != 1) {
            throw new IllegalArgumentException("WebAuthnAssertionAttr version 은 1 이어야 합니다: " + version);
        }
        if (authenticatorData == null || authenticatorData.length == 0) {
            throw new IllegalArgumentException("authenticatorData 가 비어 있습니다");
        }
        if (clientDataJSON == null || clientDataJSON.length == 0) {
            throw new IllegalArgumentException("clientDataJSON 이 비어 있습니다");
        }
        authenticatorData = authenticatorData.clone();
        clientDataJSON = clientDataJSON.clone();
        credentialId = credentialId == null ? null : credentialId.clone();
        aaguid = aaguid == null ? null : aaguid.clone();
    }

    /** version=1 로 생성하는 편의 팩토리. */
    public static WebAuthnAssertionAttr of(byte[] authenticatorData, byte[] clientDataJSON,
                                           int coseAlg, byte[] credentialId, byte[] aaguid) {
        return new WebAuthnAssertionAttr(1, authenticatorData, clientDataJSON, coseAlg, credentialId, aaguid);
    }

    @Override
    public byte[] authenticatorData() {
        return authenticatorData.clone();
    }

    @Override
    public byte[] clientDataJSON() {
        return clientDataJSON.clone();
    }

    @Override
    public byte[] credentialId() {
        return credentialId == null ? null : credentialId.clone();
    }

    @Override
    public byte[] aaguid() {
        return aaguid == null ? null : aaguid.clone();
    }

    /** DER(ASN.1) 인코딩. OPTIONAL 은 IMPLICIT 태그로 인코딩한다. */
    public byte[] toDer() {
        try {
            return toAsn1().getEncoded(ASN1Encoding.DER);
        } catch (IOException e) {
            throw new IllegalStateException("WebAuthnAssertionAttr DER 인코딩 실패", e);
        }
    }

    /** ASN.1 표현(SET OF 수납 시 재파싱 없이 사용). */
    public ASN1Sequence toAsn1() {
        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(new ASN1Integer(version));
        v.add(new DEROctetString(authenticatorData));
        v.add(new DEROctetString(clientDataJSON));
        v.add(new ASN1Integer(coseAlg));
        if (credentialId != null) {
            v.add(new DERTaggedObject(false, CRED_ID_TAG, new DEROctetString(credentialId)));
        }
        if (aaguid != null) {
            v.add(new DERTaggedObject(false, AAGUID_TAG, new DEROctetString(aaguid)));
        }
        return new DERSequence(v);
    }

    /** DER(ASN.1) 디코딩. */
    public static WebAuthnAssertionAttr fromDer(byte[] der) {
        try {
            return fromAsn1(ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(der)));
        } catch (IOException e) {
            throw new IllegalArgumentException("WebAuthnAssertionAttr DER 파싱 실패", e);
        }
    }

    /** ASN.1 표현으로부터 디코딩. */
    public static WebAuthnAssertionAttr fromAsn1(ASN1Sequence seq) {
        if (seq.size() < 4 || seq.size() > 6) {
            throw new IllegalArgumentException("WebAuthnAssertionAttr 필드 개수 오류: " + seq.size());
        }
        int version = ASN1Integer.getInstance(seq.getObjectAt(0)).intValueExact();
        byte[] authData = ASN1OctetString.getInstance(seq.getObjectAt(1)).getOctets();
        byte[] clientData = ASN1OctetString.getInstance(seq.getObjectAt(2)).getOctets();
        int coseAlg = ASN1Integer.getInstance(seq.getObjectAt(3)).intValueExact();

        byte[] credentialId = null;
        byte[] aaguid = null;
        for (int i = 4; i < seq.size(); i++) {
            ASN1TaggedObject tagged = ASN1TaggedObject.getInstance(seq.getObjectAt(i));
            switch (tagged.getTagNo()) {
                case CRED_ID_TAG -> credentialId = ASN1OctetString.getInstance(tagged, false).getOctets();
                case AAGUID_TAG -> aaguid = ASN1OctetString.getInstance(tagged, false).getOctets();
                default -> throw new IllegalArgumentException(
                        "알 수 없는 OPTIONAL 태그: [" + tagged.getTagNo() + "]");
            }
        }
        return new WebAuthnAssertionAttr(version, authData, clientData, coseAlg, credentialId, aaguid);
    }

    // --- 배열 깊은 비교를 위한 equals/hashCode 재정의 ---

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WebAuthnAssertionAttr other)) {
            return false;
        }
        return version == other.version
                && coseAlg == other.coseAlg
                && Arrays.equals(authenticatorData, other.authenticatorData)
                && Arrays.equals(clientDataJSON, other.clientDataJSON)
                && Arrays.equals(credentialId, other.credentialId)
                && Arrays.equals(aaguid, other.aaguid);
    }

    @Override
    public int hashCode() {
        int result = Integer.hashCode(version);
        result = 31 * result + Integer.hashCode(coseAlg);
        result = 31 * result + Arrays.hashCode(authenticatorData);
        result = 31 * result + Arrays.hashCode(clientDataJSON);
        result = 31 * result + Arrays.hashCode(credentialId);
        result = 31 * result + Arrays.hashCode(aaguid);
        return result;
    }

    @Override
    public String toString() {
        return "WebAuthnAssertionAttr{version=" + version + ", coseAlg=" + coseAlg
                + ", authenticatorData=" + authenticatorData.length + "B"
                + ", clientDataJSON=" + clientDataJSON.length + "B"
                + ", credentialId=" + (credentialId == null ? "none" : credentialId.length + "B")
                + ", aaguid=" + (aaguid == null ? "none" : aaguid.length + "B") + "}";
    }
}
