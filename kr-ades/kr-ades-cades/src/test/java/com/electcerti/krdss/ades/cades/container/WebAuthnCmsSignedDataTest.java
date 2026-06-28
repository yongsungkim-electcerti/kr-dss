package com.electcerti.krdss.ades.cades.container;

import com.electcerti.krdss.ades.cades.KrAdesOids;
import com.electcerti.krdss.ades.cades.bind.HashSuite;
import com.electcerti.krdss.ades.cades.bind.SignatureBindingService;
import com.electcerti.krdss.ades.cades.bind.SignedAttrsBuilder;
import org.bouncycastle.asn1.cms.CMSObjectIdentifiers;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.asn1.cms.SignedData;
import org.bouncycastle.asn1.cms.SignerInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 특허-A 정식 CMS 승격 — RFC5652 SignedData 조립/파싱 + 결속 바이트 보존 +
 * 표준 {@link CMSSignedData} 로드 가능 + 모사 컨테이너와 동일 Parsed 환원 검증.
 */
class WebAuthnCmsSignedDataTest {

    private static X509Certificate cert;
    private static byte[] signedAttrsDer;
    private static final byte[] ASSERTION_SIG = "demo-assertion-signature".getBytes(StandardCharsets.UTF_8);

    private final WebAuthnCmsSignedData cms = new WebAuthnCmsSignedData();

    @BeforeAll
    static void setup() throws Exception {
        cert = selfSigned("CN=KR-DSS CMS Signer");
        byte[] docDigest = MessageDigest.getInstance("SHA-256").digest("계약서".getBytes(StandardCharsets.UTF_8));
        SignedAttrsBuilder.SignedAttrs attrs = SignedAttrsBuilder.build(
                docDigest, Instant.parse("2026-06-28T00:00:00Z"), cert, HashSuite.SHA_256);
        signedAttrsDer = attrs.der();
    }

    private WebAuthnAssertionAttr sampleAttr() {
        return WebAuthnAssertionAttr.of(
                "auth-data".getBytes(StandardCharsets.UTF_8),
                "{\"type\":\"webauthn.get\"}".getBytes(StandardCharsets.UTF_8),
                -7, "cred-id".getBytes(StandardCharsets.UTF_8), new byte[16]);
    }

    @Test
    void assemble_then_parse_preserves_all_fields() {
        WebAuthnAssertionAttr attr = sampleAttr();
        byte[] container = cms.assemble(signedAttrsDer, HashSuite.SHA_256, ASSERTION_SIG, List.of(cert), attr);

        WebAuthnCmsAssembler.Parsed parsed = cms.parse(container);
        assertThat(parsed.signedAttrsDer()).containsExactly(signedAttrsDer);
        assertThat(parsed.sigAlgOid()).isEqualTo(KrAdesOids.WEBAUTHN_ASSERTION_SIG_ALG);
        assertThat(parsed.signature()).containsExactly(ASSERTION_SIG);
        assertThat(parsed.certificates()).hasSize(1);
        assertThat(parsed.certificates().get(0)).isEqualTo(cert);
        assertThat(parsed.assertion()).isEqualTo(attr);
    }

    @Test
    void signedAttrs_bytes_preserved_so_challenge_rederives_identically() {
        byte[] container = cms.assemble(signedAttrsDer, HashSuite.SHA_256, ASSERTION_SIG, List.of(cert), sampleAttr());
        WebAuthnCmsAssembler.Parsed parsed = cms.parse(container);

        String expected = SignatureBindingService.deriveChallenge(signedAttrsDer, HashSuite.SHA_256);
        String rederived = SignatureBindingService.deriveChallenge(parsed.signedAttrsDer(), HashSuite.SHA_256);
        assertThat(rederived).isEqualTo(expected);
    }

    @Test
    void output_is_a_standard_cms_signedData_loadable_by_bouncycastle() throws Exception {
        byte[] container = cms.assemble(signedAttrsDer, HashSuite.SHA_256, ASSERTION_SIG, List.of(cert), sampleAttr());

        // 1) ContentInfo == id-signedData
        ContentInfo ci = ContentInfo.getInstance(container);
        assertThat(ci.getContentType()).isEqualTo(CMSObjectIdentifiers.signedData);

        // 2) 표준 CMS API 로 로드 가능(detached) — SignerInfo 1개, 인증서 1개
        CMSSignedData sd = new CMSSignedData(container);
        assertThat(sd.getSignerInfos().size()).isEqualTo(1);
        assertThat(sd.getCertificates().getMatches(null)).hasSize(1);

        // 3) digestAlgorithm = SHA-256, signatureAlgorithm = KR-DSS WebAuthn 사설 OID
        SignedData lowLevel = SignedData.getInstance(ci.getContent());
        SignerInfo si = SignerInfo.getInstance(lowLevel.getSignerInfos().getObjectAt(0));
        assertThat(si.getDigestAlgorithm().getAlgorithm().getId()).isEqualTo(HashSuite.SHA_256.oid());
        assertThat(si.getDigestEncryptionAlgorithm().getAlgorithm().getId())
                .isEqualTo(KrAdesOids.WEBAUTHN_ASSERTION_SIG_ALG);
    }

    @Test
    void assertion_lives_in_unsignedAttrs_not_in_signedAttrs() throws Exception {
        byte[] container = cms.assemble(signedAttrsDer, HashSuite.SHA_256, ASSERTION_SIG, List.of(cert), sampleAttr());
        WebAuthnCmsAssembler.Parsed parsed = cms.parse(container);

        assertThat(parsed.assertion()).isNotNull();
        byte[] oidDer = new org.bouncycastle.asn1.ASN1ObjectIdentifier(
                KrAdesOids.WEBAUTHN_ASSERTION_ATTR).getEncoded();
        assertThat(indexOf(parsed.signedAttrsDer(), oidDer)).isEqualTo(-1);
        assertThat(indexOf(container, oidDer)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void parse_rejects_non_signedData_container() {
        // 모사 컨테이너(커스텀 SEQUENCE)는 정식 CMS 파서가 거부 → 라우터가 모사 경로로 위임
        WebAuthnCmsAssembler mock = new WebAuthnCmsAssembler();
        byte[] mockContainer = mock.assemble(signedAttrsDer, ASSERTION_SIG, List.of(cert), sampleAttr());
        assertThatThrownBy(() -> cms.parse(mockContainer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static X509Certificate selfSigned(String dn) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair kp = kpg.generateKeyPair();
        X500Name name = new X500Name(dn);
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        var builder = new JcaX509v3CertificateBuilder(
                name, BigInteger.valueOf(1),
                Date.from(now), Date.from(now.plusSeconds(3600L * 24 * 365)),
                name, kp.getPublic());
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(kp.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }
}
