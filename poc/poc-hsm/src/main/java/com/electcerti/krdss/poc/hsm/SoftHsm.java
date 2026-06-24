package com.electcerti.krdss.poc.hsm;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 실증용 소프트HSM. 서명키쌍과 인증서를 메모리에 보관하고 서명연산을 수행한다.
 *
 * <p>운영 HSM 의 경계를 모사한다: 개인키는 이 클래스 밖으로 노출되지 않으며,
 * {@link #sign} 으로 다이제스트에 대한 서명값만 외부에 제공한다.</p>
 */
@Component
public class SoftHsm {

    /** SHA-256 DigestInfo 접두부(RFC 8017) — 다이제스트를 PKCS#1 v1.5 서명에 사용하기 위함. */
    private static final byte[] SHA256_DIGEST_INFO_PREFIX = {
            0x30, 0x31, 0x30, 0x0d, 0x06, 0x09, 0x60, (byte) 0x86, 0x48, 0x01,
            0x65, 0x03, 0x04, 0x02, 0x01, 0x05, 0x00, 0x04, 0x20
    };

    private final Map<String, KeyEntry> keystore = new LinkedHashMap<>();

    /** 보관 항목: 개인키(HSM 내부) + 서명용 인증서. */
    public record KeyEntry(PrivateKey privateKey, X509Certificate certificate, String keyAlgo, int keyLen) {
    }

    @PostConstruct
    void init() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        // 실증용 서명자 키 1종을 부팅 시 생성한다.
        provision("krdss-remote-signer", "CN=KR-DSS Remote Signer (DEMO), O=ELECTCERTI, C=KR");
    }

    private void provision(String alias, String subjectDn) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();

        Instant now = Instant.now();
        X500Name dn = new X500Name(subjectDn);
        X509v3CertificateBuilder builder = new X509v3CertificateBuilder(
                dn,
                BigInteger.valueOf(now.toEpochMilli()),
                Date.from(now),
                Date.from(now.plus(825, ChronoUnit.DAYS)),
                dn,
                org.bouncycastle.asn1.x509.SubjectPublicKeyInfo.getInstance(kp.getPublic().getEncoded()));
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(kp.getPrivate());
        X509CertificateHolder holder = builder.build(signer);
        X509Certificate cert = new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);

        keystore.put(alias, new KeyEntry(kp.getPrivate(), cert, "RSA", 2048));
    }

    public Map<String, KeyEntry> entries() {
        return keystore;
    }

    public KeyEntry require(String alias) {
        KeyEntry entry = keystore.get(alias);
        if (entry == null) {
            throw new IllegalArgumentException("알 수 없는 키 별칭: " + alias);
        }
        return entry;
    }

    /**
     * 다이제스트에 대해 PKCS#1 v1.5(RSA) 서명을 생성한다.
     *
     * <p>입력은 이미 계산된 SHA-256 다이제스트이며, DigestInfo 로 감싼 뒤
     * {@code NONEwithRSA} 로 서명해 검증 가능한 표준 서명값을 만든다.</p>
     *
     * @param alias  서명키 별칭
     * @param digest SHA-256 다이제스트(32바이트)
     * @return 서명값
     */
    public byte[] sign(String alias, byte[] digest) throws Exception {
        KeyEntry entry = require(alias);
        byte[] digestInfo = new byte[SHA256_DIGEST_INFO_PREFIX.length + digest.length];
        System.arraycopy(SHA256_DIGEST_INFO_PREFIX, 0, digestInfo, 0, SHA256_DIGEST_INFO_PREFIX.length);
        System.arraycopy(digest, 0, digestInfo, SHA256_DIGEST_INFO_PREFIX.length, digest.length);

        Signature sig = Signature.getInstance("NONEwithRSA", BouncyCastleProvider.PROVIDER_NAME);
        sig.initSign(entry.privateKey());
        sig.update(digestInfo);
        return sig.sign();
    }
}
