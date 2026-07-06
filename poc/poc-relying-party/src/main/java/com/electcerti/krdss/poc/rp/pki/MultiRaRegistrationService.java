package com.electcerti.krdss.poc.rp.pki;

import com.electcerti.krdss.dss.pki.AttestationVerificationResult;
import com.electcerti.krdss.dss.pki.AuthenticatorGrade;
import com.electcerti.krdss.dss.pki.CertificateAuthority;
import com.electcerti.krdss.dss.pki.CertificateLifecycleManager;
import com.electcerti.krdss.dss.pki.CertificateStatus;
import com.electcerti.krdss.dss.pki.HsmAttestation;
import com.electcerti.krdss.dss.pki.HsmAttestationVerifier;
import com.electcerti.krdss.dss.pki.HsmCertificateIssuer;
import com.electcerti.krdss.dss.pki.HsmGrade;
import com.electcerti.krdss.dss.pki.HsmIssuedCertificate;
import com.electcerti.krdss.dss.pki.InMemoryAuthenticatorMetadataRegistry;
import com.electcerti.krdss.dss.pki.InMemoryHsmDeviceRegistry;
import com.electcerti.krdss.dss.pki.IssuedCertificate;
import com.electcerti.krdss.dss.pki.KrPkiOids;
import com.electcerti.krdss.dss.pki.MultiRaCertificateService;
import com.electcerti.krdss.dss.pki.RegistrationAuthority;
import com.electcerti.krdss.dss.pki.RegistrationBindingService;
import com.electcerti.krdss.dss.pki.RegistrationResult;
import com.electcerti.krdss.poc.rp.local.WebAuthnDemoCa;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 특허-B PoC 오케스트레이션 — 단일 WebAuthn 등록 → 복수 RA 인증서 + 생명주기 + HSM 발급.
 *
 * <p>특허-A Mode 1 의 {@link WebAuthnDemoCa} 와 <b>동일한 CA 인스턴스</b>를 공유하여,
 * {@code kr-dss-pki} 의 {@link MultiRaCertificateService}/{@link CertificateLifecycleManager}/
 * {@link HsmCertificateIssuer} 를 데모로 묶는다.</p>
 *
 * <p>데모 RA 2종(신원확인형 정부24 / 플랫폼형 은행연합)을 사전 구성한다.</p>
 */
@Service
public class MultiRaRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(MultiRaRegistrationService.class);

    private final MultiRaCertificateService multiRa;
    private final CertificateLifecycleManager lifecycle;
    private final HsmCertificateIssuer hsmIssuer;
    private final InMemoryAuthenticatorMetadataRegistry authRegistry;
    private final InMemoryHsmDeviceRegistry hsmRegistry;

    private final Map<String, RegistrationAuthority> raById = new LinkedHashMap<>();
    private final Map<BigInteger, IssuedCertificate> bySerial = new ConcurrentHashMap<>();
    private final Map<String, RegistrationResult> regByKid = new ConcurrentHashMap<>();

    public MultiRaRegistrationService(WebAuthnDemoCa demoCa) {
        CertificateAuthority ca = demoCa.delegate();
        this.authRegistry = new InMemoryAuthenticatorMetadataRegistry();
        this.hsmRegistry = new InMemoryHsmDeviceRegistry();
        RegistrationBindingService binding = new RegistrationBindingService(ca, authRegistry);
        this.multiRa = new MultiRaCertificateService(binding);
        this.lifecycle = new CertificateLifecycleManager(binding);
        this.hsmIssuer = new HsmCertificateIssuer(ca, new HsmAttestationVerifier(hsmRegistry));

        register(new RegistrationAuthority("RA-GOV", "정부24",
                RegistrationAuthority.RaType.IDENTITY, KrPkiOids.raPolicy("1")));
        register(new RegistrationAuthority("RA-BANK", "은행연합",
                RegistrationAuthority.RaType.PLATFORM, KrPkiOids.raPolicy("2")));
    }

    private void register(RegistrationAuthority ra) {
        raById.put(ra.raId(), ra);
    }

    /** 데모 구성: AAGUID 보안 등급 등록. */
    public void seedAuthenticatorGrade(byte[] aaguid, AuthenticatorGrade grade) {
        authRegistry.register(aaguid, grade);
    }

    /** 데모 구성: hsmDeviceId 보안 등급 등록. */
    public void seedHsmDevice(byte[] hsmDeviceId, HsmGrade grade) {
        hsmRegistry.register(hsmDeviceId, grade);
    }

    public List<RegistrationAuthority> registrationAuthorities() {
        return List.copyOf(raById.values());
    }

    // === 단일 등록 → 복수 RA 인증서 (특허-B 청구항 2) ===

    public record CertView(String raId, String raName, String raType, String grade,
                           String policyOid, String serial, String keyIdentifier,
                           String status, String certificatePem) {
    }

    /**
     * 단일 WebAuthn 등록 결과로 모든 RA 인증서를 발급하고 생명주기에 등록한다.
     */
    public List<CertView> registerMulti(byte[] spki, String credentialIdB64, int coseAlg,
                                        byte[] aaguid, boolean attestationVerified) {
        PublicKey publicKey = parsePublicKey(spki);
        byte[] credentialId = Base64.getUrlDecoder().decode(credentialIdB64);
        AttestationVerificationResult attestation = attestationVerified
                ? AttestationVerificationResult.verified("packed")
                : AttestationVerificationResult.unverified();
        RegistrationResult reg = new RegistrationResult(publicKey, aaguid, credentialId, coseAlg, attestation);

        List<RegistrationAuthority> ras = registrationAuthorities();
        List<IssuedCertificate> issued = multiRa.issueForAll(reg, ras, subjectCn(credentialIdB64));

        String kid = CertificateAuthority.keyIdentifierHex(publicKey);
        regByKid.put(kid, reg);
        List<CertView> views = new ArrayList<>(issued.size());
        for (IssuedCertificate ic : issued) {
            lifecycle.track(ic);
            bySerial.put(ic.certificate().getSerialNumber(), ic);
            views.add(view(ic));
        }
        log.info("[PatentB] 단일 등록 → {} RA 인증서 발급 kid={} grade={}",
                issued.size(), shortKid(kid), issued.isEmpty() ? "-" : issued.get(0).grade());
        return views;
    }

    /** SubjectKeyIdentifier 기준 연관 인증서 조회(청구항 13). */
    public List<CertView> findByKeyIdentifier(String kid) {
        List<CertView> views = new ArrayList<>();
        for (IssuedCertificate ic : multiRa.findByKeyIdentifier(kid)) {
            views.add(view(ic));
        }
        return views;
    }

    // === 생명주기 (특허-B 청구항 8·9) ===

    public CertView suspend(String serial) {
        lifecycle.suspend(toSerial(serial));
        return view(requireCert(serial));
    }

    public CertView resume(String serial) {
        lifecycle.resume(toSerial(serial));
        return view(requireCert(serial));
    }

    public CertView revoke(String serial) {
        lifecycle.revoke(toSerial(serial));
        return view(requireCert(serial));
    }

    public CertificateStatus status(String serial) {
        return lifecycle.status(toSerial(serial));
    }

    /** 갱신: Attestation 재검증으로 등급·정책 OID 재결정, 동일 SPKI 발급, 기존 폐지(청구항 8). */
    public CertView renew(String serial, boolean attestationVerified) {
        IssuedCertificate old = requireCert(serial);
        RegistrationResult reg0 = regByKid.get(old.keyIdentifierHex());
        if (reg0 == null) {
            throw new IllegalArgumentException("원 등록 결과를 찾을 수 없음: kid=" + old.keyIdentifierHex());
        }
        RegistrationAuthority ra = raById.get(old.raId());
        AttestationVerificationResult att = attestationVerified
                ? AttestationVerificationResult.verified("packed")
                : AttestationVerificationResult.unverified();
        RegistrationResult fresh = new RegistrationResult(
                reg0.credentialPublicKey(), reg0.aaguid(), reg0.credentialId(), reg0.coseAlg(), att);

        var outcome = lifecycle.renew(old, fresh, ra, subjectCn(old.keyIdentifierHex()), true);
        IssuedCertificate renewed = outcome.renewed();
        bySerial.put(renewed.certificate().getSerialNumber(), renewed);
        log.info("[PatentB] 갱신 serial={} 등급 {}→{} (변동={})", serial,
                outcome.previousGrade(), outcome.currentGrade(), outcome.gradeChanged());
        return view(renewed);
    }

    // === HSM 발급 (특허-B 청구항 10·15) ===

    public record HsmCertView(String raId, String grade, boolean nonExtractable,
                              String serial, String keyIdentifier, String certificatePem) {
    }

    public HsmCertView issueHsm(byte[] csrDer, byte[] hsmDeviceId, byte[] hsmInstanceId,
                                boolean nonExtractable, String securityLevel, boolean signatureVerified,
                                String raId) {
        RegistrationAuthority ra = raById.getOrDefault(raId, registrationAuthorities().get(0));
        HsmAttestation attestation = new HsmAttestation(
                hsmDeviceId, hsmInstanceId, nonExtractable, securityLevel, signatureVerified);
        HsmIssuedCertificate issued = hsmIssuer.issueFromCsr(csrDer, attestation, ra, "KR-DSS HSM Signer");
        log.info("[PatentB] HSM 인증서 발급 ra={} grade={} nonExtractable={}",
                ra.raId(), issued.grade(), issued.nonExtractable());
        return new HsmCertView(issued.raId(), issued.grade().name(), issued.nonExtractable(),
                issued.certificate().getSerialNumber().toString(), issued.keyIdentifierHex(),
                toPem(issued.certificate()));
    }

    /**
     * HSM 데모 발급: HSM 을 모사하여 서버에서 키쌍·CSR 을 생성한 뒤 발급한다(브라우저는 CSR 미생성).
     */
    public HsmCertView issueHsmDemo(HsmGrade grade, boolean nonExtractable, boolean signatureVerified) {
        byte[] deviceId = "DEMO-HSM-01".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        seedHsmDevice(deviceId, grade);
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair hsmKey = kpg.generateKeyPair();
            var builder = new JcaPKCS10CertificationRequestBuilder(
                    new X500Name("CN=KR-DSS HSM Signer"), hsmKey.getPublic());
            ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(hsmKey.getPrivate());
            byte[] csr = builder.build(signer).getEncoded();
            return issueHsm(csr, deviceId, new byte[]{1}, nonExtractable, "EAL4+", signatureVerified, "RA-BANK");
        } catch (Exception e) {
            throw new IllegalStateException("HSM 데모 CSR 생성 실패", e);
        }
    }

    // === helpers ===

    private CertView view(IssuedCertificate ic) {
        RegistrationAuthority ra = raById.get(ic.raId());
        BigInteger serial = ic.certificate().getSerialNumber();
        return new CertView(
                ic.raId(),
                ra != null ? ra.name() : ic.raId(),
                ra != null ? ra.type().name() : "-",
                ic.grade().name(),
                ra != null ? ra.policyOid() : "-",
                serial.toString(),
                ic.keyIdentifierHex(),
                lifecycle.status(serial).name(),
                toPem(ic.certificate()));
    }

    private IssuedCertificate requireCert(String serial) {
        IssuedCertificate ic = bySerial.get(toSerial(serial));
        if (ic == null) {
            throw new IllegalArgumentException("미발급/미추적 인증서 serial=" + serial);
        }
        return ic;
    }

    private static BigInteger toSerial(String serial) {
        try {
            return new BigInteger(serial.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("잘못된 serial 형식: " + serial);
        }
    }

    private static String subjectCn(String credentialIdB64) {
        return "KR-DSS Multi-RA Signer";
    }

    private PublicKey parsePublicKey(byte[] spki) {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(spki);
        for (String alg : new String[]{"EC", "RSA"}) {
            try {
                return KeyFactory.getInstance(alg).generatePublic(spec);
            } catch (Exception ignored) {
                // 다음 알고리즘 시도
            }
        }
        throw new IllegalArgumentException("공개키(SPKI) 파싱 실패 — EC/RSA 만 지원");
    }

    private static String shortKid(String kid) {
        return kid == null || kid.length() <= 12 ? kid : kid.substring(0, 12) + "…";
    }

    private static String toPem(X509Certificate cert) {
        try {
            String b64 = Base64.getEncoder().encodeToString(cert.getEncoded());
            StringBuilder sb = new StringBuilder("-----BEGIN CERTIFICATE-----\n");
            for (int i = 0; i < b64.length(); i += 64) {
                sb.append(b64, i, Math.min(i + 64, b64.length())).append('\n');
            }
            return sb.append("-----END CERTIFICATE-----\n").toString();
        } catch (Exception e) {
            return "(인증서 인코딩 실패)";
        }
    }
}
