package com.electcerti.krdss.poc.rp.tlpoc;

import com.electcerti.krdss.tl.builder.KrTrustListXml;
import com.electcerti.krdss.tl.builder.KrTrustListXmlSigner;
import com.electcerti.krdss.tl.model.KrTrustList;
import com.electcerti.krdss.tl.model.TrustServiceTimeline;
import com.electcerti.krdss.tl.model.PocTrustListProfile;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.cert.*;
import java.time.*;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

/** 로컬 PoC 수신 경계. 앵커는 서버 설정에서만 읽고 후보 파일은 신뢰하지 않는다. */
public final class TlAcceptance {
    public static final int MAX_TL = 5 * 1024 * 1024;
    private final Path root;
    private final List<X509Certificate> anchors;
    private final Clock clock;
    private Snapshot current;
    private Candidate candidate;
    private record Candidate(String id, byte[] xml, String baseline, boolean ready) { }
    public record Review(String candidateId, boolean ready, String reason, TlReview.Document document,
            TlReview.Document baseline, List<TlReview.Change> changes, List<String> anchors) { }

    public synchronized Review review(byte[] input) throws Exception {
        candidate = null;
        if (input.length == 0 || input.length > MAX_TL) throw new Rejected("TL_SIZE_LIMIT");
        byte[] xml = input.clone(); String reason = "CHECK_PASSED"; boolean ready = true;
        try { var list = inspect(xml, true); checkOrder(list, hash(xml)); }
        catch (Rejected e) { ready = false; reason = e.getMessage(); }
        catch (Exception e) { ready = false; reason = "TL_PARSE_ERROR"; }
        TlReview.Document document = null;
        try { document = TlReview.describe(xml); } catch (Exception ignored) {
            if (ready) reason = "TL_DISPLAY_ERROR";
            ready = false;
        }
        String id = UUID.randomUUID().toString();
        candidate = new Candidate(id, xml, current == null ? null : current.sha256(), ready);
        var baseline = document();
        return new Review(id, ready, reason, document, baseline, TlReview.compare(baseline, document),
                anchors.stream().map(c -> c.getSubjectX500Principal().getName()).toList());
    }
    public synchronized View apply(String id) throws Exception {
        if (candidate == null || !candidate.id().equals(id)) throw new Rejected("CANDIDATE_CHANGED");
        if (!candidate.ready()) throw new Rejected("CANDIDATE_REJECTED");
        if (!Objects.equals(candidate.baseline(), current == null ? null : current.sha256())) throw new Rejected("BASELINE_CHANGED");
        View result = adopt(candidate.xml()); candidate = null; return result;
    }
    public synchronized TlReview.Document document() throws Exception {
        return current == null ? null : TlReview.describe(current.xml());
    }
    public synchronized byte[] xml(String id) {
        if ("active".equals(id)) { if (current == null) throw new Rejected("NO_VALID_TL"); return current.xml(); }
        if (candidate == null || !candidate.id().equals(id)) throw new Rejected("CANDIDATE_CHANGED");
        return candidate.xml().clone();
    }

    public record Snapshot(String id, String sequence, String sha256, Instant acceptedAt,
            Instant nextUpdate, KrTrustList list, byte[] xml) {
        public Snapshot { xml = xml.clone(); }
        @Override public byte[] xml() { return xml.clone(); }
    }
    public record View(String id, String sequence, String sha256, Instant acceptedAt,
            Instant nextUpdate, boolean usable) { }
    public static final class Rejected extends IllegalArgumentException {
        public Rejected(String code) { super(code); }
    }
    public TlAcceptance(Path root, List<X509Certificate> anchors, Clock clock) throws Exception {
        this.root = root; this.anchors = List.copyOf(anchors); this.clock = clock;
        Files.createDirectories(root);
        if (Files.exists(root.resolve("active.xml"))) {
            byte[] xml = Files.readAllBytes(root.resolve("active.xml"));
            // 재기동 시 서명·구조 재검사. 만료본은 보관하되 사용 시 거부한다.
            var list = inspect(xml, false);
            current = snapshot(xml, list, Files.getLastModifiedTime(root.resolve("active.xml")).toInstant());
        }
    }
    public synchronized View view() {
        return current == null ? null : new View(current.id(), current.sequence(), current.sha256(),
                current.acceptedAt(), current.nextUpdate(), current.nextUpdate().isAfter(clock.instant()));
    }
    public synchronized Snapshot capture() {
        if (current == null || !current.nextUpdate().isAfter(clock.instant())) throw new Rejected("NO_VALID_TL");
        return current;
    }
    public synchronized View adopt(byte[] xml) throws Exception {
        KrTrustList list;
        try { list = inspect(xml, true); }
        catch (Rejected e) { throw e; }
        catch (Exception e) { throw new Rejected("TL_PARSE_ERROR"); }
        String hash = hash(xml);
        checkOrder(list, hash);
        if (current != null && hash.equals(current.sha256())) return view();
        var next = snapshot(xml, list, clock.instant());
        atomic(root.resolve("active.xml"), xml);
        current = next;
        return view();
    }
    private void checkOrder(KrTrustList list, String hash) {
        if (current != null) {
            int order = list.schemeInformation().sequenceNumber().compareTo(current.list().schemeInformation().sequenceNumber());
            if (order < 0) throw new Rejected("TL_ROLLBACK");
            if (order == 0) {
                if (!hash.equals(current.sha256())) throw new Rejected("TL_CONFLICT");
                return;
            }
            if (!list.schemeInformation().issueDate().isAfter(current.list().schemeInformation().issueDate()))
                throw new Rejected("TL_ISSUE_TIME_REGRESSION");
        }
    }
    private Snapshot snapshot(byte[] xml, KrTrustList list, Instant at) {
        String h = hash(xml);
        return new Snapshot(h, list.schemeInformation().sequenceNumber().toString(), h, at,
                list.schemeInformation().nextUpdate(), list, xml);
    }
    private KrTrustList inspect(byte[] xml, boolean fresh) throws Exception {
        if (xml.length == 0 || xml.length > MAX_TL) throw new Rejected("TL_SIZE_LIMIT");
        var doc = xmlFactory().newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        if (!"TrustServiceStatusList".equals(doc.getDocumentElement().getLocalName())) throw new Rejected("TL_PARSE_ERROR");
        var check = KrTrustListXmlSigner.check(xml);
        if (!check.valid()) throw new Rejected("TL_SIGNATURE_INVALID");
        var certs = new ArrayList<X509Certificate>();
        var nodes = doc.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "X509Certificate");
        if (nodes.getLength() > 20) throw new Rejected("CERT_COUNT_LIMIT");
        for (int i = 0; i < nodes.getLength(); i++) certs.add(certificate(Base64.getMimeDecoder().decode(nodes.item(i).getTextContent())));
        if (!chain(check.signer(), certs, anchors, clock.instant())) throw new Rejected("TL_CHAIN_UNTRUSTED");
        var list = KrTrustListXml.fromXml(xml);
        var scheme = list.schemeInformation();
        if (scheme.sequenceNumber().signum() <= 0 || !scheme.nextUpdate().isAfter(scheme.issueDate())
                || scheme.issueDate().isAfter(clock.instant())) throw new Rejected("TL_METADATA_INVALID");
        if (fresh && !scheme.nextUpdate().isAfter(clock.instant())) throw new Rejected("TL_EXPIRED");
        for (var provider : list.trustServiceProviders()) for (var service : provider.services()) {
            if (TrustServiceTimeline.resolve(service, clock.instant()).reason() == TrustServiceTimeline.Reason.INVALID_HISTORY)
                throw new Rejected("TL_HISTORY_INVALID");
            if (PocTrustListProfile.status(service.current().statusUri()) == KrTrustList.ServiceStatus.UNKNOWN)
                throw new Rejected("TL_STATUS_UNSUPPORTED");
            for (var history : service.history()) if (PocTrustListProfile.status(history.statusUri()) == KrTrustList.ServiceStatus.UNKNOWN)
                throw new Rejected("TL_STATUS_UNSUPPORTED");
            checkExtensions(service.current().extensions());
            for (var history : service.history()) checkExtensions(history.extensions());
        }
        return list;
    }
    private static void checkExtensions(List<KrTrustList.ServiceExtension> extensions) throws Exception {
        for (var extension : extensions) if (extension.critical()) for (var xml : extension.payloadXml()) {
            var element = xmlFactory().newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8))).getDocumentElement();
            String name = element.getLocalName(), value = element.getTextContent().trim();
            boolean known = PocTrustListProfile.EXTENSION_NS.equals(element.getNamespaceURI())
                    && (("TrustDomain".equals(name) && Set.of("joint", "simple").contains(value))
                    || ("TrustPointLevel".equals(name) && Set.of("0", "1").contains(value)));
            if (!known) throw new Rejected("TL_CRITICAL_EXTENSION_UNSUPPORTED");
        }
    }
    static DocumentBuilderFactory xmlFactory() throws Exception {
        var f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return f;
    }
    public static X509Certificate certificate(byte[] bytes) throws Exception {
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(bytes));
    }
    public static List<X509Certificate> certificates(byte[] bytes) throws Exception {
        if (bytes.length > 1024 * 1024) throw new Rejected("CERT_SIZE_LIMIT");
        var result = CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(bytes));
        if (result.size() > 20) throw new Rejected("CERT_COUNT_LIMIT");
        return result.stream().map(c -> (X509Certificate)c).toList();
    }
    static boolean chain(X509Certificate leaf, List<X509Certificate> materials, List<X509Certificate> trusted, Instant at) {
        try {
            leaf.checkValidity(Date.from(at));
            if (trusted.contains(leaf)) return true;
            var selector = new X509CertSelector(); selector.setCertificate(leaf);
            var set = new HashSet<TrustAnchor>();
            for (var cert : trusted) { cert.checkValidity(Date.from(at)); set.add(new TrustAnchor(cert, null)); }
            var params = new PKIXBuilderParameters(set, selector); params.setRevocationEnabled(false); params.setDate(Date.from(at));
            params.addCertStore(CertStore.getInstance("Collection", new CollectionCertStoreParameters(materials)));
            CertPathBuilder.getInstance("PKIX").build(params); return true;
        } catch (Exception e) { return false; }
    }
    static String hash(byte[] data) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    static void atomic(Path target, byte[] bytes) throws Exception {
        Path tmp = Files.createTempFile(target.getParent(), ".pending-", ".tmp");
        try { Files.write(tmp, bytes); Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(tmp); }
    }
}
