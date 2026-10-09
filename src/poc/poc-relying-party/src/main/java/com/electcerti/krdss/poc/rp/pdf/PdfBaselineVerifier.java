package com.electcerti.krdss.poc.rp.pdf;

import com.electcerti.krdss.dss.api.VerificationStatus;
import com.electcerti.krdss.tl.builder.SignedKrTrustList;
import com.electcerti.krdss.tl.model.KrTrustList;
import eu.europa.esig.dss.alert.SilentOnStatusAlert;
import eu.europa.esig.dss.diagnostic.CertificateWrapper;
import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.enumerations.Indication;
import eu.europa.esig.dss.enumerations.SubIndication;
import eu.europa.esig.dss.jaxb.object.Message;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 서명된 PDF 검증 — 트러스트스토어(신뢰 앵커) + KR-TL(신뢰목록) 이중 입력.
 *
 * <p>흐름: DSS 로 문서 서명의 무결성·인증서 경로를 검증하고, 별도로 KR-TL 배포본 자체의
 * 전자서명을 검증한 뒤, 두 결과를 결합해 최종 3분류 판정을 낸다. Whale PoC 는 TSA·OCSP 를
 * 쓰지 않으므로 폐지정보 조회는 끄고, 그로 인한 경고가 검증을 중단시키지 않도록
 * 알림을 침묵 처리한다.</p>
 */
@Service
public class PdfBaselineVerifier {

    private static final Logger log = LoggerFactory.getLogger(PdfBaselineVerifier.class);

    /** DSS 기본 정책에서 폐지정보 관련 제약만 낮춘 PoC 전용 검증정책. */
    private static final String POLICY_RESOURCE = "/pdf/whale-validation-policy.xml";

    /**
     * @param pdf            서명된 PDF
     * @param pdfName        파일명
     * @param trustAnchors   트러스트스토어에서 적재한 신뢰 앵커
     * @param trustStoreName 트러스트스토어 파일명(리포트 표기용)
     * @param trustList      KR-TL 배포본(XML/JSON, 선택 — 없으면 신뢰목록 평가를 생략한다)
     */
    public PdfVerifyReport verify(byte[] pdf, String pdfName, List<X509Certificate> trustAnchors,
                                  String trustStoreName, KrTrustListDocument trustList) {
        Objects.requireNonNull(pdf, "pdf");

        DSSDocument document = new InMemoryDocument(pdf, pdfName != null ? pdfName : "signed.pdf");
        SignedDocumentValidator validator;
        try {
            validator = SignedDocumentValidator.fromDocument(document);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "서명 문서를 해석하지 못했습니다. 서명된 PDF 가 맞는지 확인하세요: " + e.getMessage(), e);
        }
        validator.setCertificateVerifier(certificateVerifier(trustAnchors));

        Reports reports = validateWithWhalePolicy(validator);
        SimpleReport simpleReport = reports.getSimpleReport();
        DiagnosticData diagnosticData = reports.getDiagnosticData();

        List<PdfVerifyReport.SignatureInfo> signatures = new ArrayList<>();
        VerificationStatus dssStatus = null;
        for (String signatureId : simpleReport.getSignatureIdList()) {
            signatures.add(describe(signatureId, simpleReport, diagnosticData));
            dssStatus = worst(dssStatus, map(simpleReport.getIndication(signatureId)));
        }

        // KR-TL 은 문서 서명과 독립적으로 평가한다. 서명이 없는 문서라도 목록 자체의
        // 서명 검증 결과는 그대로 보여 주는 편이 원인 파악에 도움이 된다.
        TrustListEvaluation evaluation = evaluateTrustList(trustList, diagnosticData, trustAnchors);

        if (signatures.isEmpty()) {
            return new PdfVerifyReport(
                    VerificationStatus.TOTAL_FAILED.name(),
                    "문서에서 전자서명을 찾지 못했습니다. " + evaluation.info().detail(),
                    Instant.now(),
                    new PdfVerifyReport.DocumentInfo(pdfName, pdf.length),
                    trustStoreInfo(trustStoreName, trustAnchors),
                    evaluation.info(),
                    List.of(),
                    reports.getXmlSimpleReport());
        }

        VerificationStatus finalStatus = combine(dssStatus, evaluation);
        String summary = summarize(finalStatus, dssStatus, evaluation, signatures.size());
        log.info("[PDF] 검증 완료 — status={}, 서명 {}건, KR-TL={}", finalStatus, signatures.size(),
                describeTrustList(evaluation.info()));

        return new PdfVerifyReport(
                finalStatus.name(),
                summary,
                toInstant(simpleReport.getValidationTime()),
                new PdfVerifyReport.DocumentInfo(pdfName, pdf.length),
                trustStoreInfo(trustStoreName, trustAnchors),
                evaluation.info(),
                signatures,
                reports.getXmlSimpleReport());
    }

    /**
     * Whale PoC 검증정책으로 검증한다.
     *
     * <p>DSS 기본 정책은 폐지정보(CRL/OCSP)가 없으면 {@code RevocationDataAvailable} 제약이
     * FAIL 이라, 트러스트 앵커가 맞아도 판정이 INDETERMINATE 로 떨어진다. 본 PoC 는 OCSP 를
     * 사용하지 않는 범위이므로 그 두 제약만 IGNORE 로 낮춘 정책을 쓴다.</p>
     */
    private static Reports validateWithWhalePolicy(SignedDocumentValidator validator) {
        try (InputStream policy = PdfBaselineVerifier.class.getResourceAsStream(POLICY_RESOURCE)) {
            if (policy == null) {
                log.warn("[PDF] 검증정책 {} 을 찾지 못해 DSS 기본 정책으로 검증한다.", POLICY_RESOURCE);
                return validator.validateDocument();
            }
            return validator.validateDocument(policy);
        } catch (IOException e) {
            throw new IllegalStateException("검증정책을 읽지 못했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 신뢰 앵커를 심은 검증기를 만든다.
     *
     * <p>TSA·OCSP 미사용 범위이므로 폐지정보 부재·서명 만료 등은 검증을 중단시키지 않고
     * 리포트의 경고로만 남도록 알림을 침묵 처리한다. 침묵시키지 않으면 DSS 가 예외를 던져
     * 리포트 자체를 받을 수 없다.</p>
     */
    private static CommonCertificateVerifier certificateVerifier(List<X509Certificate> trustAnchors) {
        CommonTrustedCertificateSource trustedSource = new CommonTrustedCertificateSource();
        for (X509Certificate certificate : trustAnchors) {
            trustedSource.addCertificate(new CertificateToken(certificate));
        }

        CommonCertificateVerifier verifier = new CommonCertificateVerifier();
        verifier.setTrustedCertSources(trustedSource);
        verifier.setCheckRevocationForUntrustedChains(false);
        verifier.setAlertOnMissingRevocationData(new SilentOnStatusAlert());
        verifier.setAlertOnNoRevocationAfterBestSignatureTime(new SilentOnStatusAlert());
        verifier.setAlertOnRevokedCertificate(new SilentOnStatusAlert());
        verifier.setAlertOnInvalidSignature(new SilentOnStatusAlert());
        verifier.setAlertOnInvalidTimestamp(new SilentOnStatusAlert());
        verifier.setAlertOnExpiredCertificate(new SilentOnStatusAlert());
        verifier.setAlertOnExpiredSignature(new SilentOnStatusAlert());
        verifier.setAlertOnNotYetValidCertificate(new SilentOnStatusAlert());
        verifier.setAlertOnUncoveredPOE(new SilentOnStatusAlert());
        return verifier;
    }

    private static PdfVerifyReport.SignatureInfo describe(String signatureId, SimpleReport simpleReport,
                                                          DiagnosticData diagnosticData) {
        SignatureWrapper signature = diagnosticData.getSignatureById(signatureId);
        CertificateWrapper signingCertificate = signature != null ? signature.getSigningCertificate() : null;

        List<String> chain = new ArrayList<>();
        if (signature != null) {
            for (CertificateWrapper certificate : signature.getCertificateChain()) {
                chain.add(certificate.getCertificateDN());
            }
        }

        SubIndication subIndication = simpleReport.getSubIndication(signatureId);
        return new PdfVerifyReport.SignatureInfo(
                signatureId,
                String.valueOf(simpleReport.getIndication(signatureId)),
                subIndication != null ? subIndication.name() : null,
                simpleReport.getSignatureFormat(signatureId) != null
                        ? simpleReport.getSignatureFormat(signatureId).name() : null,
                simpleReport.getSignedBy(signatureId),
                signingCertificate != null ? signingCertificate.getCertificateIssuerDN() : null,
                signingCertificate != null ? signingCertificate.getSerialNumber() : null,
                signature != null ? toInstant(signature.getClaimedSigningTime()) : null,
                chain,
                messages(simpleReport.getAdESValidationErrors(signatureId)),
                messages(simpleReport.getAdESValidationWarnings(signatureId)),
                messages(simpleReport.getAdESValidationInfo(signatureId)));
    }

    private static List<String> messages(List<Message> messages) {
        return messages == null ? List.of() : messages.stream().map(Message::getValue).toList();
    }

    // ====================================================================
    // KR-TL 평가
    // ====================================================================

    /**
     * KR-TL 을 평가한다.
     *
     * <p>두 단계다. ① 배포본 자체의 전자서명이 유효하고 서명자가 신뢰 앵커로 이어지는가,
     * ② 그 목록에 서명자 발급기관이 GRANTED 로 실려 있는가. ①이 깨지면 목록을 판정 근거로
     * 쓰지 않는다 — 아무나 만든 목록으로 "공인됨"을 주장할 수 있으면 안 되기 때문이다.</p>
     */
    private static TrustListEvaluation evaluateTrustList(KrTrustListDocument document, DiagnosticData diagnosticData,
                                                         List<X509Certificate> trustAnchors) {
        if (document == null) {
            return new TrustListEvaluation(
                    new PdfVerifyReport.TrustListInfo(false, null, null, null, null, null, 0, null, null,
                            "KR-TL 이 제출되지 않아 신뢰목록 평가를 생략했습니다."),
                    false);
        }

        PdfVerifyReport.TrustListSignature signature = document.format() == KrTrustListDocument.Format.XML
                ? verifyXmlTrustListSignature(document, trustAnchors)
                : verifyJwsTrustListSignature(document, trustAnchors);
        KrTrustList trustList = document.trustList();
        KrTrustList.SchemeInformation scheme = trustList.schemeInformation();

        List<KrTrustList.TrustService> services = new ArrayList<>();
        if (trustList.trustServiceProviders() != null) {
            for (KrTrustList.TrustServiceProvider provider : trustList.trustServiceProviders()) {
                if (provider.services() != null) {
                    services.addAll(provider.services());
                }
            }
        }

        // 서명자를 직접 발급한 CA 를 먼저 찾고, 없을 때만 상위 체인으로 넓힌다.
        // 둘을 한 뭉치로 다루면 신뢰목록에 실린 순서에 따라 루트가 먼저 걸려,
        // 리포트에 실제 발급기관 대신 루트가 표시된다.
        List<String> issuerDns = new ArrayList<>();
        List<String> chainDns = new ArrayList<>();
        for (SignatureWrapper signatureWrapper : diagnosticData.getSignatures()) {
            CertificateWrapper signing = signatureWrapper.getSigningCertificate();
            if (signing != null) {
                issuerDns.add(normalize(signing.getCertificateIssuerDN()));
            }
            for (CertificateWrapper certificate : signatureWrapper.getCertificateChain()) {
                chainDns.add(normalize(certificate.getCertificateDN()));
            }
        }

        KrTrustList.TrustService match = findService(services, issuerDns);
        if (match == null) {
            match = findService(services, chainDns);
        }

        boolean granted = match != null && match.status() == KrTrustList.ServiceStatus.GRANTED;
        String detail;
        if (!signature.trustworthy()) {
            // 목록을 신뢰할 수 없으면 등재 여부를 따지는 것 자체가 무의미하다.
            detail = signature.detail();
        } else if (match == null) {
            detail = "서명자 발급기관과 일치하는 신뢰 서비스를 KR-TL 에서 찾지 못했습니다.";
        } else if (granted) {
            detail = "서명자 발급기관이 KR-TL 에 GRANTED 상태로 등재되어 있습니다.";
        } else {
            detail = "서명자 발급기관이 KR-TL 에 등재되어 있으나 상태가 " + match.status() + " 입니다.";
        }

        return new TrustListEvaluation(
                new PdfVerifyReport.TrustListInfo(true, signature,
                        scheme != null ? scheme.operatorName() : null,
                        scheme != null ? scheme.sequenceNumber().toString() : null,
                        scheme != null ? scheme.issueDate() : null,
                        scheme != null ? scheme.nextUpdate() : null,
                        services.size(),
                        match != null ? match.serviceName() : null,
                        match != null ? match.status().name() : null,
                        detail),
                signature.trustworthy() && granted);
    }

    /**
     * XML(TS 119 612) KR-TL 의 XAdES enveloped 서명을 검증한다.
     *
     * <p>PDF 와 똑같이 EU DSS 문서 검증기를 태운다. 트러스트 앵커와 검증정책이 같으므로
     * "문서 서명은 통과하는데 신뢰목록 서명만 다른 기준으로 통과" 같은 어긋남이 생기지 않는다.</p>
     */
    private static PdfVerifyReport.TrustListSignature verifyXmlTrustListSignature(KrTrustListDocument document,
                                                                                  List<X509Certificate> trustAnchors) {
        String format = document.format().label();
        SignedDocumentValidator validator;
        try {
            validator = SignedDocumentValidator.fromDocument(new InMemoryDocument(document.raw(), "kr-tl.xml"));
        } catch (Exception e) {
            return new PdfVerifyReport.TrustListSignature(format, false, false, false, false, null, null, List.of(),
                    null, null, "KR-TL XML 을 서명 문서로 해석하지 못했습니다: " + e.getMessage());
        }
        validator.setCertificateVerifier(certificateVerifier(trustAnchors));

        Reports reports = validateWithWhalePolicy(validator);
        SimpleReport simpleReport = reports.getSimpleReport();
        List<String> signatureIds = simpleReport.getSignatureIdList();
        if (signatureIds.isEmpty()) {
            return new PdfVerifyReport.TrustListSignature(format, false, false, false, false, null, null, List.of(),
                    null, null, "KR-TL XML 에 전자서명이 없습니다. 진본성을 확인할 수 없습니다.");
        }

        String signatureId = signatureIds.get(0);
        Indication indication = simpleReport.getIndication(signatureId);
        SubIndication subIndication = simpleReport.getSubIndication(signatureId);
        SignatureWrapper signature = reports.getDiagnosticData().getSignatureById(signatureId);

        List<String> chainDns = new ArrayList<>();
        if (signature != null) {
            for (CertificateWrapper certificate : signature.getCertificateChain()) {
                chainDns.add(certificate.getCertificateDN());
            }
        }

        // TOTAL_FAILED 는 서명값·해시가 깨진 경우다. INDETERMINATE 는 서명 자체는 온전하지만
        // 신뢰 경로를 세우지 못한 경우이므로 "서명값 유효 / 서명자 불신"으로 나눠 보고한다.
        boolean signatureValid = indication != Indication.TOTAL_FAILED;
        boolean signerTrusted = indication == Indication.TOTAL_PASSED;
        String detail;
        if (!signatureValid) {
            detail = "KR-TL XML 서명이 본문과 일치하지 않습니다. 배포 후 변조되었을 수 있습니다.";
        } else if (signerTrusted) {
            detail = "KR-TL XAdES 서명이 유효하고, 서명자 인증서가 트러스트스토어 앵커까지 이어집니다.";
        } else {
            detail = "KR-TL XAdES 서명은 온전하지만 신뢰 경로를 세우지 못했습니다"
                    + (subIndication != null ? " (" + subIndication.name() + ")" : "") + ".";
        }

        return new PdfVerifyReport.TrustListSignature(format, true, signatureValid, signerTrusted, signerTrusted,
                simpleReport.getSignatureFormat(signatureId) != null
                        ? simpleReport.getSignatureFormat(signatureId).name() : null,
                simpleReport.getSignedBy(signatureId), chainDns,
                String.valueOf(indication), subIndication != null ? subIndication.name() : null, detail);
    }

    /**
     * JSON(JWS) KR-TL 의 서명을 검증한다.
     *
     * <p>서명값 자체는 {@link SignedKrTrustList} 가 이미 확인했다. 여기서는 남은 한 가지 —
     * 서명자 인증서가 이용기관이 신뢰하는 앵커까지 이어지는지 — 를 본다.</p>
     */
    private static PdfVerifyReport.TrustListSignature verifyJwsTrustListSignature(KrTrustListDocument document,
                                                                                  List<X509Certificate> trustAnchors) {
        SignedKrTrustList jws = document.jws();
        String format = document.format().label();
        List<String> chainDns = jws.signerChain().stream()
                .map(certificate -> certificate.getSubjectX500Principal().getName())
                .toList();
        X509Certificate signer = jws.signerCertificate();
        String signedBy = signer != null ? signer.getSubjectX500Principal().getName() : null;

        if (!jws.signed() || !jws.signatureValid()) {
            return new PdfVerifyReport.TrustListSignature(format, jws.signed(), jws.signatureValid(), false, false,
                    jws.algorithm(), signedBy, chainDns, null, null, jws.detail());
        }

        boolean signerTrusted = chainsToAnchor(jws.signerChain(), trustAnchors);
        String detail = signerTrusted
                ? "KR-TL 전자서명이 유효하고, 서명자 인증서가 트러스트스토어 앵커까지 이어집니다."
                : "KR-TL 전자서명은 유효하지만 서명자 인증서가 트러스트스토어 앵커로 이어지지 않습니다.";
        return new PdfVerifyReport.TrustListSignature(format, true, true, signerTrusted, signerTrusted,
                jws.algorithm(), signedBy, chainDns, null, null, detail);
    }

    /**
     * 서명자 체인이 신뢰 앵커에 닿는지 PKIX 경로 검증으로 확인한다.
     *
     * <p>PoC 범위상 폐지정보를 쓰지 않으므로 revocation 검사는 끈다.</p>
     */
    private static boolean chainsToAnchor(List<X509Certificate> chain, List<X509Certificate> trustAnchors) {
        if (chain.isEmpty() || trustAnchors.isEmpty()) {
            return false;
        }
        // 체인에 앵커 자신이 들어 있으면 경로에서 뺀다. PKIX 는 앵커를 경로에 포함하지 않는다.
        List<X509Certificate> path = new ArrayList<>(chain);
        path.removeIf(trustAnchors::contains);
        if (path.isEmpty()) {
            // 서명자 인증서 자체가 앵커로 등록된 경우.
            return true;
        }
        try {
            Set<TrustAnchor> anchors = new HashSet<>();
            for (X509Certificate anchor : trustAnchors) {
                anchors.add(new TrustAnchor(anchor, null));
            }
            PKIXParameters parameters = new PKIXParameters(anchors);
            parameters.setRevocationEnabled(false);
            CertPath certPath = CertificateFactory.getInstance("X.509").generateCertPath(path);
            CertPathValidator.getInstance("PKIX").validate(certPath, parameters);
            return true;
        } catch (Exception e) {
            log.debug("[PDF] KR-TL 서명자 경로 검증 실패: {}", e.getMessage());
            return false;
        }
    }

    /** 주어진 DN 목록 가운데 하나를 발급 주체로 갖는 신뢰 서비스를 찾는다. */
    private static KrTrustList.TrustService findService(List<KrTrustList.TrustService> services, List<String> dns) {
        for (KrTrustList.TrustService service : services) {
            String subject = subjectOf(service.digitalIdentity());
            if (subject != null && dns.contains(normalize(subject))) {
                return service;
            }
        }
        return null;
    }

    private static String subjectOf(byte[] digitalIdentity) {
        if (digitalIdentity == null || digitalIdentity.length == 0) {
            return null;
        }
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate =
                    (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(digitalIdentity));
            return certificate.getSubjectX500Principal().getName();
        } catch (Exception e) {
            return null;
        }
    }

    /** DN 비교용 정규화 — 공백·대소문자 차이로 어긋나는 것을 막는다. */
    private static String normalize(String dn) {
        return dn == null ? "" : dn.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    // ====================================================================
    // 판정 결합
    // ====================================================================

    private static VerificationStatus map(Indication indication) {
        if (indication == Indication.TOTAL_PASSED || indication == Indication.PASSED) {
            return VerificationStatus.TOTAL_PASSED;
        }
        if (indication == Indication.TOTAL_FAILED || indication == Indication.FAILED) {
            return VerificationStatus.TOTAL_FAILED;
        }
        return VerificationStatus.INDETERMINATE;
    }

    /** 서명이 여러 개면 가장 나쁜 판정을 문서 전체 판정으로 삼는다. */
    private static VerificationStatus worst(VerificationStatus current, VerificationStatus candidate) {
        if (current == null) {
            return candidate;
        }
        if (current == VerificationStatus.TOTAL_FAILED || candidate == VerificationStatus.TOTAL_FAILED) {
            return VerificationStatus.TOTAL_FAILED;
        }
        if (current == VerificationStatus.INDETERMINATE || candidate == VerificationStatus.INDETERMINATE) {
            return VerificationStatus.INDETERMINATE;
        }
        return VerificationStatus.TOTAL_PASSED;
    }

    /**
     * DSS 판정에 KR-TL 평가를 결합한다.
     *
     * <p>암호학적으로 유효해도 발급기관이 신뢰목록에 없으면 "유효"라고 단정할 수 없어
     * INDETERMINATE 로 낮춘다. 신뢰목록 자체의 서명이 깨진 경우도 마찬가지다.
     * KR-TL 미제출은 평가를 하지 않은 것이므로 낮추지 않는다.</p>
     */
    private static VerificationStatus combine(VerificationStatus dssStatus, TrustListEvaluation evaluation) {
        if (dssStatus != VerificationStatus.TOTAL_PASSED) {
            return dssStatus;
        }
        if (!evaluation.info().provided()) {
            return VerificationStatus.TOTAL_PASSED;
        }
        return evaluation.granted() ? VerificationStatus.TOTAL_PASSED : VerificationStatus.INDETERMINATE;
    }

    private static String summarize(VerificationStatus finalStatus, VerificationStatus dssStatus,
                                    TrustListEvaluation evaluation, int signatureCount) {
        StringBuilder summary = new StringBuilder();
        summary.append("서명 ").append(signatureCount).append("건 — ");
        if (finalStatus == VerificationStatus.TOTAL_PASSED) {
            summary.append("무결성·인증서 경로 검증 통과");
        } else if (dssStatus != VerificationStatus.TOTAL_PASSED) {
            summary.append("무결성 또는 인증서 경로 검증에서 문제가 확인됨");
        } else {
            summary.append("암호학적 검증은 통과했으나 신뢰목록 평가에서 확인 불가");
        }
        summary.append(". ").append(evaluation.info().detail());
        return summary.toString();
    }

    private static String describeTrustList(PdfVerifyReport.TrustListInfo info) {
        if (!info.provided()) {
            return "미제출";
        }
        PdfVerifyReport.TrustListSignature signature = info.signature();
        return "서명=" + (signature != null && signature.trustworthy() ? "신뢰" : "불신")
                + ", 등재=" + (info.serviceStatus() != null ? info.serviceStatus() : "없음");
    }

    private static PdfVerifyReport.TrustStoreInfo trustStoreInfo(String fileName, List<X509Certificate> anchors) {
        List<String> subjects = anchors.stream()
                .map(certificate -> certificate.getSubjectX500Principal().getName())
                .toList();
        return new PdfVerifyReport.TrustStoreInfo(fileName, anchors.size(), subjects);
    }

    private static Instant toInstant(Date date) {
        return date != null ? date.toInstant() : null;
    }

    private record TrustListEvaluation(PdfVerifyReport.TrustListInfo info, boolean granted) {
    }
}
