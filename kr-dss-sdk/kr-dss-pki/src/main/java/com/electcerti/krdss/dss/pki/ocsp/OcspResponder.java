package com.electcerti.krdss.dss.pki.ocsp;

import org.bouncycastle.asn1.ocsp.OCSPObjectIdentifiers;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.Extensions;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.BasicOCSPRespBuilder;
import org.bouncycastle.cert.ocsp.CertificateID;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPReq;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.OCSPRespBuilder;
import org.bouncycastle.cert.ocsp.Req;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.bouncycastle.cert.ocsp.UnknownStatus;
import org.bouncycastle.cert.ocsp.jcajce.JcaBasicOCSPRespBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * RFC 6960 OCSP 응답부 — 가상 인정사업자(CA)의 인증서 폐지 상태를 서명된 OCSP 응답으로 제공한다.
 *
 * <p>요청부는 표준 {@code application/ocsp-request}(DER)를 받아 다음을 수행한다:</p>
 * <ol>
 *   <li>요청의 각 {@link CertificateID}가 이 CA(issuer)에 대한 것인지 이름·키 해시로 검증한다.</li>
 *   <li>본 CA 발급 건이면 {@link OcspStatusSource}로 일련번호별 폐지 상태를 조회한다.</li>
 *   <li>good / revoked(사유·시각) / unknown 을 {@link BasicOCSPResp}로 조립하고 서명한다.</li>
 *   <li>요청에 nonce가 있으면 그대로 응답에 반영한다(재전송 공격 방지, RFC 6960 §4.4.1).</li>
 * </ol>
 *
 * <p>본 데모에서는 CA 인증서 자체가 응답 서명자(직접 서명 모델)이므로, {@code issuerCert}와
 * {@code signerCert}는 동일 인스턴스를 전달한다. 위임 응답자(delegated responder)를 쓰려면
 * id-pkix-ocsp-nocheck 확장을 가진 별도 서명자 인증서를 전달하면 된다.</p>
 *
 * <p>스레드 안전: 상태를 보유하지 않으며 {@link OcspStatusSource} 구현의 스레드 안전성에 의존한다.</p>
 */
public final class OcspResponder {

    private final X509CertificateHolder issuerHolder;
    private final X509Certificate signerCert;
    private final PrivateKey signerKey;
    private final X509CertificateHolder[] chainHolders;
    private final OcspStatusSource statusSource;
    private final DigestCalculatorProvider digestProvider;
    private final String signatureAlgorithm;

    /**
     * @param issuerCert   폐지 상태를 관장하는 발급 CA 인증서(요청 CertID 검증 기준)
     * @param signerCert   OCSP 응답 서명자 인증서(직접 서명 모델이면 {@code issuerCert}와 동일)
     * @param signerKey    서명자 개인키
     * @param chain        응답에 동봉할 인증서 체인(서명자→상위). 최소 서명자 1건.
     * @param statusSource 일련번호별 폐지 상태 원천
     */
    public OcspResponder(X509Certificate issuerCert, X509Certificate signerCert, PrivateKey signerKey,
                         List<X509Certificate> chain, OcspStatusSource statusSource) {
        Objects.requireNonNull(issuerCert, "issuerCert");
        this.signerCert = Objects.requireNonNull(signerCert, "signerCert");
        this.signerKey = Objects.requireNonNull(signerKey, "signerKey");
        this.statusSource = Objects.requireNonNull(statusSource, "statusSource");
        try {
            this.issuerHolder = new JcaX509CertificateHolder(issuerCert);
            this.digestProvider = new JcaDigestCalculatorProviderBuilder().build();
            List<X509CertificateHolder> holders = new ArrayList<>();
            if (chain == null || chain.isEmpty()) {
                holders.add(new JcaX509CertificateHolder(signerCert));
            } else {
                for (X509Certificate c : chain) {
                    holders.add(new JcaX509CertificateHolder(c));
                }
            }
            this.chainHolders = holders.toArray(new X509CertificateHolder[0]);
            this.signatureAlgorithm = signatureAlgorithmFor(signerKey);
        } catch (Exception e) {
            throw new IllegalStateException("OCSP 응답부 초기화 실패", e);
        }
    }

    /**
     * DER 인코딩된 OCSP 요청을 처리하고 DER 인코딩된 OCSP 응답을 반환한다.
     * 요청이 손상되었거나 처리 중 오류가 나면 {@code malformedRequest} 응답을 반환한다.
     *
     * @param requestDer {@code application/ocsp-request} 본문(DER)
     * @return {@code application/ocsp-response} 본문(DER)
     */
    public byte[] respond(byte[] requestDer) {
        try {
            OCSPReq request = new OCSPReq(requestDer);

            JcaBasicOCSPRespBuilder respBuilder = new JcaBasicOCSPRespBuilder(
                    signerCert.getPublicKey(), digestProvider.get(CertificateID.HASH_SHA1));

            // nonce 반영(있을 때만) — 재전송 공격 방지
            Extension nonce = request.getExtension(OCSPObjectIdentifiers.id_pkix_ocsp_nonce);
            if (nonce != null) {
                respBuilder.setResponseExtensions(new Extensions(nonce));
            }

            Date now = new Date();
            for (Req req : request.getRequestList()) {
                CertificateID certId = req.getCertID();
                CertificateStatus status = resolve(certId);
                respBuilder.addResponse(certId, status, now, (Date) null);
            }

            ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithm).build(signerKey);
            BasicOCSPResp basic = respBuilder.build(signer, chainHolders, now);
            OCSPResp response = new OCSPRespBuilder().build(OCSPRespBuilder.SUCCESSFUL, basic);
            return response.getEncoded();
        } catch (Exception e) {
            return malformed();
        }
    }

    /** 요청 CertID → 이 CA 발급 검증 후 상태 해석. */
    private CertificateStatus resolve(CertificateID certId) throws Exception {
        if (!certId.matchesIssuer(issuerHolder, digestProvider)) {
            // 다른 CA에 대한 질의 — 이 응답부의 권한 밖
            return new UnknownStatus();
        }
        OcspCertStatus s = statusSource.lookup(certId.getSerialNumber());
        return switch (s.kind()) {
            case GOOD -> CertificateStatus.GOOD;
            case REVOKED -> new RevokedStatus(Date.from(s.revokedAt()), s.reason());
            case UNKNOWN -> new UnknownStatus();
        };
    }

    private byte[] malformed() {
        try {
            return new OCSPRespBuilder().build(OCSPRespBuilder.MALFORMED_REQUEST, null).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("OCSP malformed 응답 생성 실패", e);
        }
    }

    private static String signatureAlgorithmFor(PrivateKey key) {
        String alg = key.getAlgorithm();
        if ("EC".equalsIgnoreCase(alg) || "ECDSA".equalsIgnoreCase(alg)) {
            return "SHA256withECDSA";
        }
        if ("RSA".equalsIgnoreCase(alg)) {
            return "SHA256withRSA";
        }
        if (alg != null && alg.startsWith("Ed")) {
            return alg; // Ed25519 / Ed448
        }
        throw new IllegalArgumentException("지원하지 않는 서명자 키 알고리즘: " + alg);
    }
}
