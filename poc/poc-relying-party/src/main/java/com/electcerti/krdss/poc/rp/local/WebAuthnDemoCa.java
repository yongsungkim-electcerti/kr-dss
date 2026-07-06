package com.electcerti.krdss.poc.rp.local;

import com.electcerti.krdss.ades.cades.KrAdesOids;
import com.electcerti.krdss.dss.pki.CertificateAuthority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

/**
 * CA 서버 브리지 — 특허-B 발급 인프라({@link CertificateAuthority})를 PoC 에서 구동한다.
 *
 * <p>설정 {@code krdss.ca.keystore} 가 지정되면 해당 PKCS#12(예: New-KISA RootCA 가 발급한
 * <b>공동인증CA</b> 키스토어)를 로드해 그 키로 최종개체 인증서를 발급한다. 설정이 비어 있으면
 * 자가서명 데모 CA 로 동작한다. 특허-A Mode 1 의 기존 동작(Credential 공개키 → {@code POLICY_WEBAUTHN}
 * 인증서)은 그대로 유지된다.</p>
 *
 * <p>{@link #delegate()} 로 노출되는 동일 CA 인스턴스를 특허-B Multi-RA 데모가 공유한다.</p>
 */
@Component
public class WebAuthnDemoCa {

    private static final Logger log = LoggerFactory.getLogger(WebAuthnDemoCa.class);

    private final CertificateAuthority ca;

    /** 테스트·기본: 자가서명 데모 CA. */
    public WebAuthnDemoCa() {
        this.ca = new CertificateAuthority("localhost");
    }

    /** 설정 기반: 키스토어가 지정되면 공동인증CA 로드, 아니면 자가서명. */
    @Autowired
    public WebAuthnDemoCa(
            @Value("${krdss.ca.keystore:}") String keystore,
            @Value("${krdss.ca.keystore-password:changeit}") String password,
            @Value("${krdss.ca.key-alias:joint-ca}") String alias,
            @Value("${krdss.ca.rp-id:localhost}") String rpId) {
        if (keystore == null || keystore.isBlank()) {
            this.ca = new CertificateAuthority(rpId);
            log.info("[CA] 자가서명 데모 CA 사용(rpId={})", rpId);
        } else {
            this.ca = loadFromKeystore(keystore, password, alias, rpId);
        }
    }

    private static CertificateAuthority loadFromKeystore(String location, String password,
                                                         String alias, String rpId) {
        try (InputStream in = openResource(location)) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, password.toCharArray());
            CertificateAuthority loaded =
                    CertificateAuthority.fromKeyStore(ks, alias, password.toCharArray(), rpId);
            log.info("[CA] 발급 CA 키스토어 로드: {} (발급CA={}, 체인 {}단)",
                    location, loaded.caCertificate().getSubjectX500Principal().getName(),
                    loaded.caChain().size());
            return loaded;
        } catch (Exception e) {
            throw new IllegalStateException("CA 키스토어 로드 실패: " + location, e);
        }
    }

    private static InputStream openResource(String location) throws Exception {
        if (location.startsWith("classpath:")) {
            String path = location.substring("classpath:".length());
            InputStream in = WebAuthnDemoCa.class.getClassLoader().getResourceAsStream(path);
            if (in == null) {
                throw new IllegalArgumentException("클래스패스 리소스 없음: " + path);
            }
            return in;
        }
        return new FileInputStream(location);
    }

    /** 특허-B 데모가 공유하는 동일 CA 인스턴스. */
    public CertificateAuthority delegate() {
        return ca;
    }

    public X509Certificate caCertificate() {
        return ca.caCertificate();
    }

    /**
     * WebAuthn 자격증명 공개키로 서명자 인증서를 발급한다(특허-A Mode 1 검증 경로 정책).
     *
     * @param subjectPublicKey Credential 공개키(= SubjectPublicKeyInfo)
     * @param subjectCn        서명자 CN
     * @param credentialId     Credential ID
     */
    public X509Certificate issue(PublicKey subjectPublicKey, String subjectCn, byte[] credentialId) {
        return ca.issue(subjectPublicKey, subjectCn, credentialId,
                List.of(KrAdesOids.POLICY_WEBAUTHN), Duration.ofDays(365));
    }
}
