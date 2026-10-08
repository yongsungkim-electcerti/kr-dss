package com.electcerti.krdss.dss.trust.hsm;

import org.bouncycastle.asn1.ASN1Boolean;
import org.bouncycastle.asn1.ASN1EncodableVector;
import org.bouncycastle.asn1.ASN1GeneralizedTime;
import org.bouncycastle.asn1.ASN1Integer;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DERGeneralizedTime;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERUTF8String;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;

/**
 * HSM Attestation Object DER 인코딩·디코딩 (특허-C 청구항 9).
 *
 * <p>구조 프레이밍: {@code HSMAttestationObject ::= SEQUENCE { tbs HSMAttestationTBS,
 * attestationSig OCTET STRING }} — 서명 대상(TBS)은 명세의 필드
 * (version, hsmDeviceId, hsmInstanceId, hsmPublicKey, keyGenStatement, securityLevel,
 * timestamp)를 담은 SEQUENCE 이며, attestationSig 는 TBS 의 DER 바이트에 대한 서명이다.</p>
 */
public final class HsmAttestationCodec {

    private HsmAttestationCodec() {
    }

    /** 서명 대상(TBS)의 DER — attestationSig 를 제외한 필드 시퀀스. */
    public static byte[] tbsDer(HsmAttestationObject o) {
        try {
            return tbsSequence(o).getEncoded("DER");
        } catch (Exception e) {
            throw new IllegalStateException("HSM Attestation TBS 인코딩 실패", e);
        }
    }

    /** 전체 객체(TBS + attestationSig)의 DER. */
    public static byte[] encode(HsmAttestationObject o) {
        try {
            ASN1EncodableVector v = new ASN1EncodableVector();
            v.add(tbsSequence(o));
            v.add(new DEROctetString(o.attestationSig()));
            return new DERSequence(v).getEncoded("DER");
        } catch (Exception e) {
            throw new IllegalStateException("HSM Attestation 인코딩 실패", e);
        }
    }

    /** DER → HsmAttestationObject. */
    public static HsmAttestationObject decode(byte[] der) {
        try {
            ASN1Sequence outer = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(der));
            ASN1Sequence tbs = ASN1Sequence.getInstance(outer.getObjectAt(0));
            byte[] sig = ASN1OctetString.getInstance(outer.getObjectAt(1)).getOctets();

            int version = ASN1Integer.getInstance(tbs.getObjectAt(0)).getValue().intValue();
            byte[] deviceId = ASN1OctetString.getInstance(tbs.getObjectAt(1)).getOctets();
            byte[] instanceId = ASN1OctetString.getInstance(tbs.getObjectAt(2)).getOctets();
            SubjectPublicKeyInfo spki = SubjectPublicKeyInfo.getInstance(tbs.getObjectAt(3));
            PublicKey pub = new JcaPEMKeyConverter().getPublicKey(spki);
            HsmKeyGenStatement kg = decodeKeyGen(ASN1Sequence.getInstance(tbs.getObjectAt(4)));
            HsmSecurityLevel sl = decodeSecurityLevel(ASN1Sequence.getInstance(tbs.getObjectAt(5)));
            Instant ts = ASN1GeneralizedTime.getInstance(tbs.getObjectAt(6)).getDate().toInstant();

            return new HsmAttestationObject(version, deviceId, instanceId, pub, kg, sl, ts, sig);
        } catch (Exception e) {
            throw new IllegalArgumentException("HSM Attestation 디코딩 실패", e);
        }
    }

    private static DERSequence tbsSequence(HsmAttestationObject o) {
        try {
            ASN1EncodableVector v = new ASN1EncodableVector();
            v.add(new ASN1Integer(o.version()));
            v.add(new DEROctetString(o.hsmDeviceId()));
            v.add(new DEROctetString(o.hsmInstanceId()));
            v.add(SubjectPublicKeyInfo.getInstance(o.hsmPublicKey().getEncoded()));
            v.add(keyGenSequence(o.keyGenStatement()));
            v.add(securityLevelSequence(o.securityLevel()));
            v.add(new DERGeneralizedTime(Date.from(o.timestamp())));
            return new DERSequence(v);
        } catch (Exception e) {
            throw new IllegalStateException("HSM Attestation TBS 구성 실패", e);
        }
    }

    private static DERSequence keyGenSequence(HsmKeyGenStatement s) {
        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(new DERUTF8String(s.algorithm()));
        v.add(new ASN1Integer(s.keySize()));
        v.add(ASN1Boolean.getInstance(s.nonExtractable()));
        v.add(new DERUTF8String(s.keyUsage()));
        return new DERSequence(v);
    }

    private static HsmKeyGenStatement decodeKeyGen(ASN1Sequence seq) {
        String algorithm = DERUTF8String.getInstance(seq.getObjectAt(0)).getString();
        int keySize = ASN1Integer.getInstance(seq.getObjectAt(1)).getValue().intValue();
        boolean nonExtractable = ASN1Boolean.getInstance(seq.getObjectAt(2)).isTrue();
        String keyUsage = DERUTF8String.getInstance(seq.getObjectAt(3)).getString();
        return new HsmKeyGenStatement(algorithm, keySize, nonExtractable, keyUsage);
    }

    private static DERSequence securityLevelSequence(HsmSecurityLevel s) {
        ASN1EncodableVector v = new ASN1EncodableVector();
        v.add(new DERUTF8String(s.ccLevel()));
        v.add(new DERUTF8String(s.fipsLevel()));
        v.add(new DERUTF8String(s.vendorLevel()));
        return new DERSequence(v);
    }

    private static HsmSecurityLevel decodeSecurityLevel(ASN1Sequence seq) {
        return new HsmSecurityLevel(
                DERUTF8String.getInstance(seq.getObjectAt(0)).getString(),
                DERUTF8String.getInstance(seq.getObjectAt(1)).getString(),
                DERUTF8String.getInstance(seq.getObjectAt(2)).getString());
    }
}
