package com.electcerti.krdss.dss.pki;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

/**
 * 단일 WebAuthn RP(rpId)로 동작하는 인증기관(CA) (특허-B 청구항 2·6).
 *
 * <p>WebAuthn 등록 결과(Credential 공개키)를 SubjectPublicKeyInfo 로 하는 X.509 인증서를
 * 발급한다. 발급 인증서에는 검증 경로/장치 등급/RA 를 식별하는 정책 OID(들)와,
 * Credential 공개키 기반 SubjectKeyIdentifier 를 포함한다. <b>동일 Credential 공개키로
 * 발급된 복수 인증서는 동일한 SubjectKeyIdentifier 를 갖는다(청구항 13).</b></p>
 *
 * <p>데모용 자체 CA 키쌍(EC P-256, 자가서명 루트)은 인스턴스 생성 시 1회 생성한다.</p>
 */
public final class CertificateAuthority {

    private final String rpId;
    private final KeyPair caKeyPair;
    private final X509Certificate caCertificate;
    private final SecureRandom random = new SecureRandom();

    /** 기본 CA(rpId = {@code ca.kr-dss.example}). */
    public CertificateAuthority() {
        this("ca.kr-dss.example");
    }

    /**
     * @param rpId CA 가 단일 WebAuthn RP 로 동작하는 도메인(청구항 6)
     */
    public CertificateAuthority(String rpId) {
        this.rpId = rpId;
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            this.caKeyPair = kpg.generateKeyPair();
            this.caCertificate = selfSignedCa(caKeyPair, rpId);
        } catch (Exception e) {
            throw new IllegalStateException("CA 초기화 실패", e);
        }
    }

    /** CA 가 단일 WebAuthn RP 로 동작하는 rpId(청구항 6). */
    public String rpId() {
        return rpId;
    }

    /** CA 인증서(자가서명 루트). */
    public X509Certificate caCertificate() {
        return caCertificate;
    }

    /**
     * Credential 공개키로 서명자 인증서를 발급한다.
     *
     * @param subjectPublicKey Credential 공개키(= SubjectPublicKeyInfo)
     * @param subjectCn        서명자 CN
     * @param credentialId     Credential ID(자격증명 식별자; 현재 SKI 는 공개키 기반)
     * @param policyOids       certificatePolicies 에 부여할 정책 OID 목록(검증경로·등급·RA)
     * @param validity         유효기간
     */
    public X509Certificate issue(PublicKey subjectPublicKey, String subjectCn, byte[] credentialId,
                                 List<String> policyOids, Duration validity) {
        try {
            X500Name issuer = new X500Name(caCertificate.getSubjectX500Principal().getName());
            X500Name subject = new X500Name("CN=" + subjectCn);
            Instant now = Instant.now();
            BigInteger serial = new BigInteger(64, random);

            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuer, serial,
                    Date.from(now.minus(Duration.ofHours(1))),
                    Date.from(now.plus(validity)),
                    subject, subjectPublicKey);

            // certificatePolicies = 검증 경로(특허-A) + 장치 등급(청구항 11) + RA 식별(청구항 2·13)
            PolicyInformation[] policies = policyOids.stream().distinct()
                    .map(oid -> new PolicyInformation(new ASN1ObjectIdentifier(oid)))
                    .toArray(PolicyInformation[]::new);
            builder.addExtension(Extension.certificatePolicies, false, new CertificatePolicies(policies));

            // SubjectKeyIdentifier — 동일 Credential 공개키 → 동일 KID(청구항 13)
            JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                    extUtils.createSubjectKeyIdentifier(subjectPublicKey));

            // 전자서명·부인방지 용도, end-entity
            builder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));

            ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(caKeyPair.getPrivate());
            return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        } catch (Exception e) {
            throw new IllegalStateException("인증서 발급 실패", e);
        }
    }

    /**
     * 공개키 기반 SubjectKeyIdentifier 를 hex 로 계산한다(연관 식별자, 청구항 13).
     * 동일 공개키는 항상 동일 값을 산출한다.
     */
    public static String keyIdentifierHex(PublicKey publicKey) {
        try {
            byte[] kid = new JcaX509ExtensionUtils()
                    .createSubjectKeyIdentifier(publicKey).getKeyIdentifier();
            return HexFormat.of().formatHex(kid);
        } catch (Exception e) {
            throw new IllegalStateException("KID 계산 실패", e);
        }
    }

    private static X509Certificate selfSignedCa(KeyPair caKeyPair, String rpId) throws Exception {
        X500Name name = new X500Name("CN=KR-DSS WebAuthn CA (" + rpId + ")");
        Instant now = Instant.now();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, BigInteger.ONE,
                Date.from(now.minus(Duration.ofHours(1))),
                Date.from(now.plus(Duration.ofDays(3650))),
                name, caKeyPair.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(caKeyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }
}
