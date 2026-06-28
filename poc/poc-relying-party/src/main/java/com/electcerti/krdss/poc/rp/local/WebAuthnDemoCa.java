package com.electcerti.krdss.poc.rp.local;

import com.electcerti.krdss.ades.cades.KrAdesOids;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 특허-A Mode 1 데모 CA (특허-B Registration Binding 의 최소 브리지).
 *
 * <p>WebAuthn 등록 결과(Credential 공개키)를 SubjectPublicKeyInfo 로 하는 X.509 인증서를
 * 발급한다. 발급 인증서에는 검증 경로 식별용 정책 OID({@code POLICY_WEBAUTHN})와
 * Credential ID 기반 SubjectKeyIdentifier 를 포함한다(특허-B 청구항 5/13 브리지).</p>
 *
 * <p>데모용 자체 CA 키쌍은 기동 시 1회 생성한다(EC P-256, 자가서명 루트).</p>
 */
@Component
public class WebAuthnDemoCa {

    private final KeyPair caKeyPair;
    private final X509Certificate caCertificate;
    private final SecureRandom random = new SecureRandom();

    public WebAuthnDemoCa() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            this.caKeyPair = kpg.generateKeyPair();
            this.caCertificate = selfSignedCa(caKeyPair);
        } catch (Exception e) {
            throw new IllegalStateException("데모 CA 초기화 실패", e);
        }
    }

    public X509Certificate caCertificate() {
        return caCertificate;
    }

    /**
     * WebAuthn 자격증명 공개키로 서명자 인증서를 발급한다.
     *
     * @param subjectPublicKey Credential 공개키(= SubjectPublicKeyInfo)
     * @param subjectCn        서명자 CN
     * @param credentialId     Credential ID(SubjectKeyIdentifier 산출용)
     */
    public X509Certificate issue(PublicKey subjectPublicKey, String subjectCn, byte[] credentialId) {
        try {
            X500Name issuer = new X500Name(caCertificate.getSubjectX500Principal().getName());
            X500Name subject = new X500Name("CN=" + subjectCn);
            Instant now = Instant.now();
            BigInteger serial = new BigInteger(64, random);

            X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                    issuer, serial,
                    Date.from(now.minus(1, ChronoUnit.HOURS)),
                    Date.from(now.plus(365, ChronoUnit.DAYS)),
                    subject, subjectPublicKey);

            // certificatePolicies = WebAuthn 검증 경로 정책(특허-A §5.2)
            builder.addExtension(Extension.certificatePolicies, false,
                    new CertificatePolicies(new PolicyInformation[]{
                            new PolicyInformation(new ASN1ObjectIdentifier(KrAdesOids.POLICY_WEBAUTHN))}));

            // SubjectKeyIdentifier(특허-B 연관 식별자 브리지) — 공개키 기반(동일 Credential → 동일 KID)
            builder.addExtension(Extension.subjectKeyIdentifier, false,
                    new JcaX509ExtensionUtils().createSubjectKeyIdentifier(subjectPublicKey));

            ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(caKeyPair.getPrivate());
            return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
        } catch (Exception e) {
            throw new IllegalStateException("WebAuthn 서명자 인증서 발급 실패", e);
        }
    }

    private static X509Certificate selfSignedCa(KeyPair caKeyPair) throws Exception {
        X500Name name = new X500Name("CN=KR-DSS WebAuthn Demo CA");
        Instant now = Instant.now();
        X509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                name, BigInteger.ONE,
                Date.from(now.minus(1, ChronoUnit.HOURS)),
                Date.from(now.plus(3650, ChronoUnit.DAYS)),
                name, caKeyPair.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(caKeyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }
}
