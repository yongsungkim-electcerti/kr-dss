package com.electcerti.krdss.dss.pki;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;

/**
 * kr-dss-pki 테스트 공용 헬퍼 — Credential 생성·인증서 확장 파싱.
 */
final class PkiTestSupport {

    private PkiTestSupport() {
    }

    /** Credential 키쌍(EC P-256, COSE ES256). */
    static KeyPair newCredentialKeyPair() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        kpg.initialize(new ECGenParameterSpec("secp256r1"));
        return kpg.generateKeyPair();
    }

    /** 검증된 attestation 을 가진 등록 결과. */
    static RegistrationResult verifiedRegistration(KeyPair credential, byte[] aaguid, byte[] credentialId) {
        return new RegistrationResult(credential.getPublic(), aaguid, credentialId, -7,
                AttestationVerificationResult.verified("packed"));
    }

    /** certificatePolicies OID 목록. */
    static List<String> policyOids(X509Certificate cert) throws Exception {
        byte[] ext = cert.getExtensionValue(Extension.certificatePolicies.getId());
        ASN1OctetString octets = ASN1OctetString.getInstance(ext);
        CertificatePolicies cps = CertificatePolicies.getInstance(ASN1Primitive.fromByteArray(octets.getOctets()));
        return Arrays.stream(cps.getPolicyInformation())
                .map(PolicyInformation::getPolicyIdentifier)
                .map(oid -> oid.getId())
                .toList();
    }

    /** SubjectKeyIdentifier(hex). */
    static String subjectKeyIdentifier(X509Certificate cert) throws Exception {
        byte[] ext = cert.getExtensionValue(Extension.subjectKeyIdentifier.getId());
        ASN1OctetString octets = ASN1OctetString.getInstance(ext);
        SubjectKeyIdentifier ski = SubjectKeyIdentifier.getInstance(ASN1Primitive.fromByteArray(octets.getOctets()));
        return HexFormat.of().formatHex(ski.getKeyIdentifier());
    }
}
