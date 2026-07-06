package com.electcerti.krdss.dss.pki;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AccessDescription;
import org.bouncycastle.asn1.x509.AuthorityInformationAccess;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
    private final List<X509Certificate> caChain;
    private final SecureRandom random = new SecureRandom();

    /** AIA(id-ad-ocsp) URI — 설정 시 발급 인증서에 OCSP 응답부 위치를 삽입(null이면 미삽입). */
    private String ocspUri;
    /** CRL DistributionPoint URI — 설정 시 발급 인증서에 CRL 배포 지점을 삽입(null이면 미삽입). */
    private String crlUri;

    /** 기본 CA(rpId = {@code ca.kr-dss.example}). */
    public CertificateAuthority() {
        this("ca.kr-dss.example");
    }

    /**
     * 자가서명 데모 CA.
     *
     * @param rpId CA 가 단일 WebAuthn RP 로 동작하는 도메인(청구항 6)
     */
    public CertificateAuthority(String rpId) {
        this.rpId = rpId;
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            this.caKeyPair = kpg.generateKeyPair();
            this.caCertificate = selfSignedCa(caKeyPair, rpId);
            this.caChain = List.of(caCertificate);
        } catch (Exception e) {
            throw new IllegalStateException("CA 초기화 실패", e);
        }
    }

    /** 외부 키스토어(상위 CA 발급)로부터 로드하는 생성자. */
    private CertificateAuthority(String rpId, KeyPair caKeyPair,
                                X509Certificate caCertificate, List<X509Certificate> caChain) {
        this.rpId = rpId;
        this.caKeyPair = caKeyPair;
        this.caCertificate = caCertificate;
        this.caChain = List.copyOf(caChain);
    }

    /**
     * PKCS#12 키스토어에서 발급용 CA 키·인증서·체인을 로드한다.
     *
     * <p>예: New-KISA RootCA 가 발급한 <b>공동인증CA</b>의 {@code .p12} 를 로드하면, 본 CA 가
     * 발급하는 최종개체 인증서는 공동인증CA 가 발급기관이 되고 New-KISA RootCA 로 체인된다.</p>
     *
     * @param keyStore 로드된 PKCS#12 키스토어
     * @param alias    CA 키 엔트리 별칭
     * @param password 키 보호 비밀번호
     * @param rpId     CA 가 단일 WebAuthn RP 로 동작하는 도메인
     */
    public static CertificateAuthority fromKeyStore(KeyStore keyStore, String alias,
                                                    char[] password, String rpId) {
        try {
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, password);
            if (privateKey == null) {
                throw new IllegalArgumentException("키스토어에 별칭 키 엔트리 없음: " + alias);
            }
            X509Certificate caCert = (X509Certificate) keyStore.getCertificate(alias);
            Certificate[] raw = keyStore.getCertificateChain(alias);
            List<X509Certificate> chain = new ArrayList<>();
            if (raw != null && raw.length > 0) {
                for (Certificate c : raw) {
                    chain.add((X509Certificate) c);
                }
            } else {
                chain.add(caCert);
            }
            return new CertificateAuthority(
                    rpId, new KeyPair(caCert.getPublicKey(), privateKey), caCert, chain);
        } catch (Exception e) {
            throw new IllegalStateException("CA 키스토어 로드 실패(alias=" + alias + ")", e);
        }
    }

    /** CA 가 단일 WebAuthn RP 로 동작하는 rpId(청구항 6). */
    public String rpId() {
        return rpId;
    }

    /** 발급 CA 인증서(자가서명 데모 시 루트, 키스토어 로드 시 공동인증CA 등). */
    public X509Certificate caCertificate() {
        return caCertificate;
    }

    /** CA 인증서 체인 [발급 CA, 상위 CA…]. 자가서명 시 단일 원소. */
    public List<X509Certificate> caChain() {
        return caChain;
    }

    /**
     * 발급 인증서에 삽입할 폐지정보 배포 지점을 설정한다(빌더 스타일, 동일 인스턴스 반환).
     *
     * <p>{@code ocspUri}가 설정되면 이후 발급되는 최종개체 인증서에 AIA(id-ad-ocsp) 확장이,
     * {@code crlUri}가 설정되면 CRL DistributionPoint 확장이 삽입되어, 검증기가 별도 설정 없이
     * 이 CA(가상 인정사업자)의 OCSP/CRL 응답부를 자동으로 찾도록 한다. {@code null}이면 해당
     * 확장을 넣지 않아 기존 동작(자가서명 데모·특허-B 발급)과 완전히 호환된다.</p>
     *
     * @param ocspUri OCSP 응답부 URI(없으면 null)
     * @param crlUri  CRL 배포 지점 URI(없으면 null)
     */
    public CertificateAuthority withEndpoints(String ocspUri, String crlUri) {
        this.ocspUri = ocspUri;
        this.crlUri = crlUri;
        return this;
    }

    /** 설정된 OCSP 응답부 URI(없으면 null). */
    public String ocspUri() {
        return ocspUri;
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
            // 발급기관 DN 은 CA 인증서의 subject 를 그대로 사용한다(문자열 왕복 시 RDN 순서가
            // 뒤집혀 체인이 끊기므로 인코딩된 DN 을 보존).
            X500Name issuer = new JcaX509CertificateHolder(caCertificate).getSubject();
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

            // AIA(id-ad-ocsp) — 검증기가 이 CA의 OCSP 응답부를 자동 탐색(설정 시에만)
            if (ocspUri != null && !ocspUri.isBlank()) {
                AccessDescription ocsp = new AccessDescription(AccessDescription.id_ad_ocsp,
                        new GeneralName(GeneralName.uniformResourceIdentifier, ocspUri));
                builder.addExtension(Extension.authorityInfoAccess, false,
                        new AuthorityInformationAccess(ocsp));
            }
            // CRL DistributionPoint — CRL 배포 지점(설정 시에만)
            if (crlUri != null && !crlUri.isBlank()) {
                DistributionPointName dpName = new DistributionPointName(new GeneralNames(
                        new GeneralName(GeneralName.uniformResourceIdentifier, crlUri)));
                DistributionPoint dp = new DistributionPoint(dpName, null, null);
                builder.addExtension(Extension.cRLDistributionPoints, false,
                        new CRLDistPoint(new DistributionPoint[] {dp}));
            }

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
