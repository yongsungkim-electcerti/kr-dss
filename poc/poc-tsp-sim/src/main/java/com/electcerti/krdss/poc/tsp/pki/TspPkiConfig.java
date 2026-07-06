package com.electcerti.krdss.poc.tsp.pki;

import com.electcerti.krdss.dss.pki.CertificateAuthority;
import com.electcerti.krdss.dss.pki.KrPkiOids;
import com.electcerti.krdss.dss.pki.RegistrationAuthority;
import com.electcerti.krdss.dss.pki.ocsp.OcspResponder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

/**
 * 가상 인정사업자 PKI 배선 — 발급 CA(공동인증CA 키스토어) · RA · OCSP 응답부를 구성한다.
 *
 * <p>{@code krdss.ca.keystore}(기본 {@code classpath:pki/joint-ca.p12})를 로드해 New-KISA
 * RootCA → 공동인증CA 체인으로 발급하는 CA를 만들고, 발급 인증서에 이 서버의 OCSP URL을
 * AIA로 삽입하도록 {@link CertificateAuthority#withEndpoints}를 설정한다. 동일 키스토어에서
 * CA 개인키를 꺼내 {@link OcspResponder}(직접 서명 모델)의 서명키로 사용한다.</p>
 */
@Configuration
public class TspPkiConfig {

    private static final Logger log = LoggerFactory.getLogger(TspPkiConfig.class);

    @Bean
    LoadedCa loadedCa(
            @Value("${krdss.ca.keystore:classpath:pki/joint-ca.p12}") String keystore,
            @Value("${krdss.ca.keystore-password:changeit}") String password,
            @Value("${krdss.ca.key-alias:joint-ca}") String alias,
            @Value("${krdss.ca.rp-id:tsp.kr-dss.example}") String rpId,
            @Value("${krdss.ocsp.url:http://localhost:8082/ocsp}") String ocspUrl) {
        try (InputStream in = openResource(keystore)) {
            KeyStore ks = KeyStore.getInstance("PKCS12");
            ks.load(in, password.toCharArray());
            CertificateAuthority ca = CertificateAuthority
                    .fromKeyStore(ks, alias, password.toCharArray(), rpId)
                    .withEndpoints(ocspUrl, null);
            PrivateKey caKey = (PrivateKey) ks.getKey(alias, password.toCharArray());
            X509Certificate caCert = (X509Certificate) ks.getCertificate(alias);
            log.info("[TSP] 가상 인정사업자 CA 로드: 발급CA={}, 체인 {}단, OCSP={}",
                    caCert.getSubjectX500Principal().getName(), ca.caChain().size(), ocspUrl);
            return new LoadedCa(ca, caCert, caKey);
        } catch (Exception e) {
            throw new IllegalStateException("가상 인정사업자 CA 키스토어 로드 실패: " + keystore, e);
        }
    }

    @Bean
    RegistrationAuthority tspRegistrationAuthority() {
        // 이 가상 인정사업자를 식별하는 RA. RA 식별 정책 OID는 발급 인증서 certificatePolicies에 부여된다.
        return new RegistrationAuthority(
                "tsp-ra-1", "가상 인정사업자 RA", RegistrationAuthority.RaType.IDENTITY,
                KrPkiOids.raPolicy("1"));
    }

    @Bean
    TspCaService tspCaService(LoadedCa loadedCa, RegistrationAuthority ra) {
        return new TspCaService(loadedCa.ca(), ra);
    }

    @Bean
    OcspResponder ocspResponder(LoadedCa loadedCa, TspCaService caService) {
        // 직접 서명 모델: CA 인증서가 곧 OCSP 응답 서명자.
        return new OcspResponder(
                loadedCa.caCert(), loadedCa.caCert(), loadedCa.caKey(),
                loadedCa.ca().caChain(), caService);
    }

    private static InputStream openResource(String location) throws Exception {
        if (location.startsWith("classpath:")) {
            String path = location.substring("classpath:".length());
            InputStream in = TspPkiConfig.class.getClassLoader().getResourceAsStream(path);
            if (in == null) {
                throw new IllegalArgumentException("클래스패스 리소스 없음: " + path);
            }
            return in;
        }
        return new FileInputStream(location);
    }

    /** 키스토어에서 로드한 CA 묶음(발급용 CA + OCSP 서명용 원시 개인키/인증서). */
    public record LoadedCa(CertificateAuthority ca, X509Certificate caCert, PrivateKey caKey) {
    }
}
