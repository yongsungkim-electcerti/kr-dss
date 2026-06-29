package com.electcerti.krdss.poc.rp.local;

import com.electcerti.krdss.ades.cades.KrAdesOids;
import com.electcerti.krdss.dss.pki.CertificateAuthority;
import org.springframework.stereotype.Component;

import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.List;

/**
 * 특허-A Mode 1 데모 CA — 특허-B 발급 인프라({@link CertificateAuthority})로의 위임 브리지.
 *
 * <p>이전에는 자체 BouncyCastle 발급 로직을 보유했으나, 특허-B {@code kr-dss-pki} 정식화 이후
 * 동일 모듈의 {@link CertificateAuthority} 에 발급을 위임한다. 특허-A Mode 1 의 기존 동작
 * (Credential 공개키 → {@code POLICY_WEBAUTHN} 인증서, 공개키 기반 SubjectKeyIdentifier)은
 * 그대로 유지된다.</p>
 *
 * <p>{@link #delegate()} 로 노출되는 동일 CA 인스턴스를 특허-B Multi-RA 데모가 공유하므로,
 * Mode 1 서명자 인증서와 Multi-RA 인증서가 같은 데모 CA 아래로 발급된다.</p>
 */
@Component
public class WebAuthnDemoCa {

    private final CertificateAuthority ca = new CertificateAuthority("localhost");

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
