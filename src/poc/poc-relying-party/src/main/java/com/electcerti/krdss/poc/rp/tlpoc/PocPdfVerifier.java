package com.electcerti.krdss.poc.rp.tlpoc;

import com.electcerti.krdss.tl.model.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cms.*;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;

/** 단일 detached PDF 실증 정책. 서명 무결성과 TL 신뢰는 독립 결과다. */
public final class PocPdfVerifier {
    public static final String POLICY = "tl-poc-single-pdf-v1";
    public static final int MAX_PDF = 25 * 1024 * 1024;
    public record Result(String integrity, String chain, String trust, String service,
            String serviceStatus, List<String> path, List<String> unchecked) {
        public Result { path = List.copyOf(path); unchecked = List.copyOf(unchecked); }
    }
    private static final List<String> UNCHECKED = List.of("폐지정보(CRL/OCSP)", "신뢰 시각 증거", "PDF 다중 서명·증분 개정");
    public Result verify(byte[] pdf, TlAcceptance.Snapshot tl, Instant at, List<X509Certificate> extra) throws Exception {
        if (pdf.length == 0 || pdf.length > MAX_PDF) throw new TlAcceptance.Rejected("PDF_SIZE_LIMIT");
        try (var doc = PDDocument.load(pdf)) {
            var signatures = doc.getSignatureDictionaries();
            if (signatures.size() != 1) throw new TlAcceptance.Rejected("SINGLE_SIGNATURE_REQUIRED");
            var sig = signatures.get(0);
            if (!Set.of("adbe.pkcs7.detached", "ETSI.CAdES.detached").contains(sig.getSubFilter()))
                throw new TlAcceptance.Rejected("FORMAT_UNSUPPORTED");
            int[] range = sig.getByteRange();
            if (range.length != 4 || range[0] != 0 || range[1] <= 0 || range[2] <= range[1] || range[3] < 0
                    || (long)range[2] + range[3] != pdf.length || range[2] > pdf.length
                    || pdf[range[1]] != '<' || pdf[range[2] - 1] != '>')
                return result("DOCUMENT_COVERAGE_INVALID", "NOT_CHECKED", "NOT_CHECKED", null, null, List.of());
            for (int i = range[1] + 1; i < range[2] - 1; i++) {
                int ch = pdf[i] & 255;
                if (Character.digit(ch, 16) < 0 && ch != 0 && ch != 9 && ch != 10 && ch != 12 && ch != 13 && ch != 32)
                    return result("DOCUMENT_COVERAGE_INVALID", "NOT_CHECKED", "NOT_CHECKED", null, null, List.of());
            }
            var cms = new CMSSignedData(new CMSProcessableByteArray(sig.getSignedContent(pdf)), sig.getContents(pdf));
            if (cms.getSignerInfos().size() != 1) throw new TlAcceptance.Rejected("SINGLE_SIGNATURE_REQUIRED");
            var signer = cms.getSignerInfos().getSigners().iterator().next();
            Collection<X509CertificateHolder> matches = cms.getCertificates().getMatches(signer.getSID());
            if (matches.size() != 1) throw new TlAcceptance.Rejected("SIGNER_CERTIFICATE_AMBIGUOUS");
            var converter = new JcaX509CertificateConverter();
            var leaf = converter.getCertificate(matches.iterator().next());
            boolean valid;
            try { valid = signer.verify(new JcaSimpleSignerInfoVerifierBuilder().build(leaf)); }
            catch (CMSException e) { valid = false; }
            var materials = new ArrayList<X509Certificate>(extra);
            for (var holder : cms.getCertificates().getMatches(null)) materials.add(converter.getCertificate(holder));
            if (materials.size() > 40) throw new TlAcceptance.Rejected("CERT_COUNT_LIMIT");
            Result trust = evaluate(leaf, materials, tl, at);
            return result(valid ? "PASSED" : "SIGNATURE_INVALID", trust.chain(), trust.trust(), trust.service(), trust.serviceStatus(), trust.path());
        }
    }
    public Result evaluate(X509Certificate leaf, List<X509Certificate> supplied, TlAcceptance.Snapshot tl, Instant at) throws Exception {
        var path = new ArrayList<String>();
        if (tl == null) return result("NOT_CHECKED", "NOT_CHECKED", "NO_VALID_TL", null, null, path);
        var materials = new ArrayList<>(supplied);
        var entries = new ArrayList<Map.Entry<X509Certificate, KrTrustList.TrustService>>();
        for (var provider : tl.list().trustServiceProviders()) for (var service : provider.services()) {
            if (!PocTrustListProfile.serviceTypeUri(PocTrustListProfile.ServiceType.CA).equals(service.serviceTypeIdentifier())) continue;
            for (var value : service.current().digitalIdentity().certificates()) {
                var cert = TlAcceptance.certificate(value.bytes());
                if (cert.getBasicConstraints() < 0) continue;
                entries.add(Map.entry(cert, service)); materials.add(cert);
            }
        }
        var current = leaf;
        var visited = new HashSet<String>();
        for (int depth = 0; depth < 20; depth++) {
            if (!visited.add(TlAcceptance.hash(current.getEncoded()))) return result("NOT_CHECKED", "CHAIN_INVALID", "NOT_ESTABLISHED", null, null, path);
            path.add(current.getSubjectX500Principal().getName() + " · " + TlAcceptance.hash(current.getPublicKey().getEncoded()));
            try { current.checkValidity(Date.from(at)); }
            catch (Exception e) { return result("NOT_CHECKED", "CERT_TIME_INVALID", "NOT_ESTABLISHED", null, null, path); }
            final var node = current;
            var matching = entries.stream().filter(e -> Arrays.equals(e.getKey().getPublicKey().getEncoded(), node.getPublicKey().getEncoded())
                    && e.getKey().getSubjectX500Principal().equals(node.getSubjectX500Principal())).toList();
            if (!matching.isEmpty()) {
                if (matching.stream().map(e -> e.getValue()).distinct().count() != 1)
                    return result("NOT_CHECKED", "NOT_CHECKED", "SERVICE_AMBIGUOUS", null, null, path);
                var entry = matching.get(0); var service = entry.getValue();
                if (!TlAcceptance.chain(leaf, materials, List.of(entry.getKey()), at))
                    return result("NOT_CHECKED", "CHAIN_INVALID", "NOT_ESTABLISHED", service.serviceName(), null, path);
                var state = TrustServiceTimeline.resolve(service, at);
                var status = state.statusUri() == null ? state.reason().name() : PocTrustListProfile.status(state.statusUri()).name();
                boolean accredited = PocTrustListProfile.STATUS_ACCREDITED.equals(state.statusUri());
                // 최초로 도달한 등재 CA의 정지를 상위 Root로 우회하지 않는다.
                return result("NOT_CHECKED", "VALID", accredited ? "TRUST_CONDITIONS_MET" : "TRUST_CONDITIONS_NOT_MET", service.serviceName(), status, path);
            }
            if (current.getIssuerX500Principal().equals(current.getSubjectX500Principal())) {
                try {
                    current.verify(current.getPublicKey());
                    boolean valid = TlAcceptance.chain(leaf, materials, List.of(current), at);
                    return result("NOT_CHECKED", valid ? "VALID" : "CHAIN_INVALID", valid ? "NOT_IN_TL" : "NOT_ESTABLISHED", null, null, path);
                } catch (Exception e) { return result("NOT_CHECKED", "CHAIN_INVALID", "NOT_ESTABLISHED", null, null, path); }
            }
            var candidates = materials.stream().filter(c -> c.getSubjectX500Principal().equals(node.getIssuerX500Principal())).toList();
            var issuers = new ArrayList<X509Certificate>();
            for (var candidate : candidates) try { node.verify(candidate.getPublicKey()); if (!issuers.contains(candidate)) issuers.add(candidate); } catch (Exception ignored) { }
            if (issuers.isEmpty()) return result("NOT_CHECKED", candidates.isEmpty() ? "CHAIN_INCOMPLETE" : "CHAIN_INVALID", "NOT_ESTABLISHED", null, null, path);
            if (issuers.stream().map(c -> TlAcceptance.hash(c.getPublicKey().getEncoded())).distinct().count() > 1)
                return result("NOT_CHECKED", "CHAIN_AMBIGUOUS", "NOT_ESTABLISHED", null, null, path);
            current = issuers.get(0);
        }
        return result("NOT_CHECKED", "CHAIN_DEPTH_LIMIT", "NOT_ESTABLISHED", null, null, path);
    }
    static Result result(String integrity, String chain, String trust, String service, String status, List<String> path) {
        return new Result(integrity, chain, trust, service, status, path, UNCHECKED);
    }
}
