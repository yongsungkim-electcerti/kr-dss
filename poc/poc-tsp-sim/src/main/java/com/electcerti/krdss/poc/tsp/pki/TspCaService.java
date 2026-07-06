package com.electcerti.krdss.poc.tsp.pki;

import com.electcerti.krdss.dss.pki.CertificateAuthority;
import com.electcerti.krdss.dss.pki.CertificateStatus;
import com.electcerti.krdss.dss.pki.RegistrationAuthority;
import com.electcerti.krdss.dss.pki.ocsp.OcspCertStatus;
import com.electcerti.krdss.dss.pki.ocsp.OcspStatusSource;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequest;
import org.bouncycastle.operator.jcajce.JcaContentVerifierProviderBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 가상 인정사업자 CA/RA 서비스 — 발급·생명주기·OCSP 상태 원천을 한 곳에서 관장한다.
 *
 * <p>특허-B 발급 인프라({@link CertificateAuthority})를 재사용해 최종개체(가입자) 인증서를 발급하고,
 * 발급 인증서를 일련번호(serial)로 추적하여 유효/정지/폐지 상태를 관리한다. 이 상태 대장이
 * {@link OcspStatusSource}로 노출되어 {@link com.electcerti.krdss.dss.pki.ocsp.OcspResponder}가
 * RFC 6960 응답을 만들 때 조회한다.</p>
 *
 * <p>발급 인증서에는 CA에 설정된 AIA(OCSP) 확장이 삽입되므로, 검증기는 인증서만 보고 이
 * 인정사업자의 OCSP 응답부를 자동으로 찾는다.</p>
 */
public final class TspCaService implements OcspStatusSource {

    private static final Duration DEFAULT_VALIDITY = Duration.ofDays(365);

    private final CertificateAuthority ca;
    private final RegistrationAuthority ra;
    private final ConcurrentHashMap<BigInteger, Entry> ledger = new ConcurrentHashMap<>();

    public TspCaService(CertificateAuthority ca, RegistrationAuthority ra) {
        this.ca = Objects.requireNonNull(ca, "ca");
        this.ra = Objects.requireNonNull(ra, "ra");
    }

    /** 발급 CA(가상 인정사업자) 인증서. */
    public X509Certificate caCertificate() {
        return ca.caCertificate();
    }

    /** 발급 CA 인증서 체인 [발급 CA, 상위 CA…]. */
    public List<X509Certificate> caChain() {
        return ca.caChain();
    }

    /** 이 CA를 식별하는 RA(등록 기관). */
    public RegistrationAuthority ra() {
        return ra;
    }

    // --- 발급(RA 신원확인 후 CA 발급) ---

    /**
     * RA 등록(신원확인) 후 서버가 가입자 키쌍(EC P-256)을 생성해 인증서를 발급한다.
     * 데모 편의를 위한 경로로, 발급된 개인키를 함께 반환한다.
     */
    public Enrolled enroll(String subjectCn) {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair kp = kpg.generateKeyPair();
            X509Certificate cert = doIssue(kp.getPublic(), subjectCn);
            return new Enrolled(cert.getSerialNumber(), cert, kp.getPrivate());
        } catch (Exception e) {
            throw new IllegalStateException("가입자 키 생성·발급 실패: " + subjectCn, e);
        }
    }

    /**
     * 가입자가 제출한 PKCS#10 CSR(자기 서명 소유증명 포함)로 인증서를 발급한다.
     * CSR 서명(소유증명, PoP)을 검증한 뒤 subject CN·공개키를 추출해 발급한다.
     */
    public X509Certificate issueFromCsr(JcaPKCS10CertificationRequest csr) {
        try {
            PublicKey pub = csr.getPublicKey();
            if (!csr.isSignatureValid(new JcaContentVerifierProviderBuilder().build(pub))) {
                throw new IllegalArgumentException("CSR 서명(소유증명) 검증 실패");
            }
            String cn = commonName(csr.getSubject());
            return doIssue(pub, cn);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("CSR 기반 발급 실패", e);
        }
    }

    private X509Certificate doIssue(PublicKey subjectPublicKey, String subjectCn) {
        X509Certificate cert = ca.issue(subjectPublicKey, subjectCn, subjectCn.getBytes(),
                List.of(ra.policyOid()), DEFAULT_VALIDITY);
        ledger.put(cert.getSerialNumber(), new Entry(cert, subjectCn));
        return cert;
    }

    // --- 생명주기(정지·폐지·재개) ---

    /** 인증서 폐지(종료 상태). */
    public void revoke(BigInteger serial, int crlReason) {
        Entry e = require(serial);
        e.status = CertificateStatus.REVOKED;
        e.eventAt = Instant.now();
        e.reason = crlReason;
    }

    /** 인증서 정지(OCSP certificateHold, 재개 가능). */
    public void suspend(BigInteger serial) {
        Entry e = require(serial);
        if (e.status == CertificateStatus.REVOKED) {
            throw new IllegalStateException("폐지된 인증서는 정지할 수 없음: " + serial.toString(16));
        }
        e.status = CertificateStatus.SUSPENDED;
        e.eventAt = Instant.now();
        e.reason = CRLReason.certificateHold;
    }

    /** 정지 해제(정지 상태에서만). */
    public void resume(BigInteger serial) {
        Entry e = require(serial);
        if (e.status != CertificateStatus.SUSPENDED) {
            throw new IllegalStateException("재개는 정지 상태에서만 가능: " + e.status);
        }
        e.status = CertificateStatus.VALID;
        e.eventAt = null;
    }

    // --- 조회 ---

    /** 발급 대장 스냅샷(발급순 무보장). */
    public List<Entry> list() {
        return new ArrayList<>(ledger.values());
    }

    /** 단건 조회(없으면 null). */
    public Entry find(BigInteger serial) {
        return ledger.get(serial);
    }

    // --- OcspStatusSource ---

    @Override
    public OcspCertStatus lookup(BigInteger serialNumber) {
        Entry e = ledger.get(serialNumber);
        if (e == null) {
            return OcspCertStatus.unknown();
        }
        return switch (e.status) {
            case VALID -> OcspCertStatus.good();
            case SUSPENDED -> OcspCertStatus.suspended(e.eventAt);
            case REVOKED -> OcspCertStatus.revoked(e.eventAt, e.reason);
        };
    }

    private Entry require(BigInteger serial) {
        Entry e = ledger.get(serial);
        if (e == null) {
            throw new IllegalArgumentException("미발급/미추적 일련번호: " + serial.toString(16));
        }
        return e;
    }

    private static String commonName(X500Name dn) {
        RDN[] rdns = dn.getRDNs(BCStyle.CN);
        if (rdns.length == 0) {
            throw new IllegalArgumentException("CSR subject에 CN이 없음: " + dn);
        }
        return rdns[0].getFirst().getValue().toString();
    }

    /** 발급 대장 항목(가변: 상태 전이). */
    public static final class Entry {
        private final X509Certificate certificate;
        private final String subjectCn;
        private volatile CertificateStatus status = CertificateStatus.VALID;
        private volatile Instant eventAt; // 정지/폐지 시각(유효 시 null)
        private volatile int reason = CRLReason.unspecified;

        Entry(X509Certificate certificate, String subjectCn) {
            this.certificate = certificate;
            this.subjectCn = subjectCn;
        }

        public X509Certificate certificate() {
            return certificate;
        }

        public String subjectCn() {
            return subjectCn;
        }

        public CertificateStatus status() {
            return status;
        }

        public BigInteger serial() {
            return certificate.getSerialNumber();
        }
    }

    /** 서버 키 생성 발급 결과(개인키 포함). */
    public record Enrolled(BigInteger serial, X509Certificate certificate, PrivateKey privateKey) {
    }
}
