package com.electcerti.krdss.poc.rp.trust;

import com.electcerti.krdss.dss.api.TrustListEvaluator;
import com.electcerti.krdss.dss.pki.AuthenticatorGrade;
import com.electcerti.krdss.dss.trust.AuthenticatorTrustEntry;
import com.electcerti.krdss.dss.trust.DeviceTrustList;
import com.electcerti.krdss.dss.trust.IntegratedTrustEvaluator;
import com.electcerti.krdss.dss.trust.IntegratedTrustVerifier;
import com.electcerti.krdss.dss.trust.KrIntegratedTrustList;
import com.electcerti.krdss.dss.trust.PolicyAutoUpdater;
import com.electcerti.krdss.dss.trust.TrustPolicy;
import com.electcerti.krdss.dss.trust.TrustServiceRegistry;
import com.electcerti.krdss.poc.rp.local.WebAuthnDemoCa;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;

/**
 * 특허-C 통합 신뢰목록 데모 구성 (A/B/C 배선).
 *
 * <p>발급 CA(공동인증CA 등)를 Layer1 신뢰서비스로, Mode 1 데모 인증기(AAGUID 전부 0, 브라우저
 * {@code attestation:'none'})를 Layer2 GRANTED HIGH 로 등재한 통합 신뢰목록을 만들고,
 * 검증 라우터에 주입할 {@link TrustListEvaluator} 를 빈으로 노출한다.</p>
 */
@Configuration
public class DemoTrustListConfig {

    /** Mode 1 데모 인증기 AAGUID(attestation none → 전부 0). */
    public static final byte[] DEMO_AAGUID = new byte[16];

    @Bean
    public KrIntegratedTrustList demoTrustList(WebAuthnDemoCa ca) {
        String caCn = commonName(ca.caCertificate().getSubjectX500Principal().getName());
        var services = new TrustServiceRegistry().register(
                new TrustServiceRegistry.TrustServiceEntry(caCn, "CA", ServiceStatus.GRANTED, "KISA"));
        var devices = new DeviceTrustList().registerAuthenticator(new AuthenticatorTrustEntry(
                DEMO_AAGUID, "KR-DSS Demo", "Passkey", "SE", "L2", "none",
                null, AuthenticatorGrade.HIGH, ServiceStatus.GRANTED));
        return new KrIntegratedTrustList(services, devices, "KISA");
    }

    @Bean
    public PolicyAutoUpdater demoPolicyAutoUpdater(KrIntegratedTrustList demoTrustList) {
        return new PolicyAutoUpdater(demoTrustList);
    }

    @Bean
    public TrustListEvaluator trustListEvaluator(KrIntegratedTrustList demoTrustList) {
        return new IntegratedTrustEvaluator(new IntegratedTrustVerifier(demoTrustList, new TrustPolicy()));
    }

    private static String commonName(String dn) {
        try {
            for (Rdn rdn : new LdapName(dn).getRdns()) {
                if ("CN".equalsIgnoreCase(rdn.getType())) {
                    return String.valueOf(rdn.getValue());
                }
            }
        } catch (Exception ignored) {
            // DN 파싱 실패 시 전체 DN 반환
        }
        return dn;
    }
}
