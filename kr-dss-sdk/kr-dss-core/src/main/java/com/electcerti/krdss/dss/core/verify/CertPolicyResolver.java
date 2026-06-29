package com.electcerti.krdss.dss.core.verify;

import com.electcerti.krdss.ades.cades.KrAdesOids;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x509.PolicyInformation;

import java.security.cert.X509Certificate;

/**
 * 정책 기반 검증 라우터 — 인증서 정책 식별자 해석(특허-A 청구항 6/7).
 *
 * <p>서명자 인증서의 {@code certificatePolicies}(2.5.29.32) 확장에서 정책 OID 를 추출해
 * 검증 경로(WEBAUTHN/HSM/STANDARD)를 결정한다. 정책 OID 가 없으면 {@code null} 을 반환하여
 * 호출부가 컨테이너 구조 기반 부트스트랩으로 분기하도록 한다(설계서 §5.2).</p>
 */
public final class CertPolicyResolver {

    private CertPolicyResolver() {
    }

    /** 검증 경로. */
    public enum Path {
        WEBAUTHN, HSM, STANDARD
    }

    /**
     * 서명자 인증서의 정책 OID 로 검증 경로를 결정한다.
     *
     * @return 매핑된 경로, 또는 정책 OID 가 없으면 {@code null}
     */
    public static Path resolve(X509Certificate signerCert) {
        if (signerCert == null) {
            return null;
        }
        byte[] ext = signerCert.getExtensionValue("2.5.29.32");
        if (ext == null) {
            return null;
        }
        try {
            // 확장값은 OCTET STRING 으로 한 번 감싸져 있다.
            byte[] policiesDer = ASN1OctetString.getInstance(ext).getOctets();
            ASN1Sequence policies = ASN1Sequence.getInstance(ASN1Primitive.fromByteArray(policiesDer));
            for (int i = 0; i < policies.size(); i++) {
                PolicyInformation pi = PolicyInformation.getInstance(policies.getObjectAt(i));
                String oid = pi.getPolicyIdentifier().getId();
                if (KrAdesOids.POLICY_WEBAUTHN.equals(oid)) {
                    return Path.WEBAUTHN;
                }
                if (KrAdesOids.POLICY_HSM.equals(oid)) {
                    return Path.HSM;
                }
                if (KrAdesOids.POLICY_STANDARD.equals(oid)) {
                    return Path.STANDARD;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
