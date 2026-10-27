package com.electcerti.krdss.poc.rp.tlpoc;

import com.electcerti.krdss.tl.builder.KrTrustListXml;
import com.electcerti.krdss.tl.model.*;
import java.io.*;
import java.time.Instant;
import java.util.*;
import javax.xml.XMLConstants;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

/** 표시용 XML 해석. 서명 신뢰 판정과 분리하며 원문을 변경하지 않는다. */
public final class TlReview {
    public record Certificate(String subject, String issuer, Instant notBefore, Instant notAfter,
            String fingerprint, String publicKey, boolean ca) { }
    public record Period(String status, Instant from, Instant until) { }
    public record Service(String key, String provider, String name, String type, String status,
            Instant since, List<Certificate> certificates, List<Period> timeline, String xmlPath,
            String metadata) { }
    public record Document(String operator, String sequence, Instant issuedAt, Instant nextUpdate,
            int providerCount, List<Service> services, List<Certificate> signingCertificates,
            String sha256, String formattedXml) { }
    public record Change(String target, String field, String before, String after, String kind) { }
    private TlReview() { }
    public static Document describe(byte[] xml) throws Exception {
        if (xml.length > TlAcceptance.MAX_TL) throw new TlAcceptance.Rejected("TL_SIZE_LIMIT");
        var dom = TlAcceptance.xmlFactory().newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        var list = KrTrustListXml.fromXml(xml);
        var services = new ArrayList<Service>(); int p = 0;
        for (var provider : list.trustServiceProviders()) {
            p++; int s = 0;
            for (var service : provider.services()) {
                s++; var current = service.current(); var certs = new ArrayList<Certificate>();
                for (var cert : current.digitalIdentity().certificates()) certs.add(certificate(cert.bytes()));
                var periods = new ArrayList<Period>(); Instant until = current.statusStartingTime();
                periods.add(new Period(status(current.statusUri()), until, null));
                for (var previous : service.history()) {
                    periods.add(new Period(status(previous.statusUri()), previous.statusStartingTime(), until)); until = previous.statusStartingTime();
                }
                Collections.reverse(periods);
                String providerKey = provider.providerId() == null ? provider.name() : provider.providerId();
                String identity = service.serviceId() == null ? "key:" + certs.stream().map(Certificate::publicKey).sorted().toList() : "id:" + service.serviceId();
                services.add(new Service(providerKey + "/" + identity, provider.name(), service.serviceName(),
                        current.serviceTypeUri(), status(current.statusUri()), current.statusStartingTime(), List.copyOf(certs),
                        List.copyOf(periods), "TrustServiceProvider[" + p + "]/TSPServices/TSPService[" + s + "]",
                        current.names().toString() + current.extensions() + service.history().stream().map(h ->
                            h.serviceTypeUri() + h.names() + h.extensions() + h.digitalIdentity().subjectName()
                            + h.digitalIdentity().subjectKeyIdentifiers().stream().map(k -> HexFormat.of().formatHex(k.bytes())).toList()).toList()));
            }
        }
        var signing = new ArrayList<Certificate>();
        var nodes = dom.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "X509Certificate");
        for (int i = 0; i < Math.min(nodes.getLength(), 20); i++) signing.add(certificate(Base64.getMimeDecoder().decode(nodes.item(i).getTextContent())));
        var factory = TransformerFactory.newInstance(); factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, ""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        var transform = factory.newTransformer(); transform.setOutputProperty(OutputKeys.INDENT, "yes");
        transform.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        var text = new StringWriter(); transform.transform(new DOMSource(dom), new StreamResult(text));
        var scheme = list.schemeInformation();
        return new Document(scheme.operatorName(), scheme.sequenceNumber().toString(), scheme.issueDate(), scheme.nextUpdate(),
                list.trustServiceProviders().size(), List.copyOf(services), List.copyOf(signing), TlAcceptance.hash(xml), text.toString());
    }
    static Certificate certificate(byte[] bytes) throws Exception {
        var cert = TlAcceptance.certificate(bytes);
        return new Certificate(cert.getSubjectX500Principal().getName(), cert.getIssuerX500Principal().getName(),
                cert.getNotBefore().toInstant(), cert.getNotAfter().toInstant(), TlAcceptance.hash(cert.getEncoded()),
                TlAcceptance.hash(cert.getPublicKey().getEncoded()), cert.getBasicConstraints() >= 0);
    }
    static String status(String uri) {
        return switch (PocTrustListProfile.status(uri)) {
            case ACCREDITED -> "인정"; case SUSPENDED -> "정지"; case WITHDRAWN -> "철회"; default -> uri;
        };
    }
    public static List<Change> compare(Document old, Document next) {
        var changes = new ArrayList<Change>(); if (next == null) return List.of();
        if (old != null) {
            changed(changes, "목록", "운영기관", old.operator(), next.operator(), "목록 정보");
            changed(changes, "목록", "발행 번호", old.sequence(), next.sequence(), "새 발행본");
            changed(changes, "목록", "발행시각", old.issuedAt(), next.issuedAt(), "목록 정보");
            changed(changes, "목록", "다음 갱신시각", old.nextUpdate(), next.nextUpdate(), "목록 정보");
            if (!old.signingCertificates().equals(next.signingCertificates())) changes.add(new Change("TL 서명", "서명 인증서",
                    old.signingCertificates().stream().map(Certificate::subject).toList().toString(),
                    next.signingCertificates().stream().map(Certificate::subject).toList().toString(), "서명 인증서 변경"));
        }
        var before = group(old == null ? List.of() : old.services()); var after = group(next.services());
        var keys = new LinkedHashSet<String>(before.keySet()); keys.addAll(after.keySet());
        for (var key : keys) {
            var a = before.getOrDefault(key, List.of()); var b = after.getOrDefault(key, List.of());
            String name = (!b.isEmpty() ? b.get(0) : a.get(0)).name();
            if (a.size() > 1 || b.size() > 1) { changes.add(new Change(name, "서비스 식별", "중복 식별자", "개별 확인 필요", "비교 모호")); continue; }
            if (a.isEmpty()) { changes.add(new Change(name, "서비스", "없음", "등재", "추가")); continue; }
            if (b.isEmpty()) { changes.add(new Change(name, "서비스", "등재", "없음", "삭제")); continue; }
            var x=a.get(0); var y=b.get(0);
            changed(changes,name,"사업자",x.provider(),y.provider(),"정보 변경");
            changed(changes,name,"서비스명",x.name(),y.name(),"정보 변경");
            changed(changes,name,"서비스 유형",x.type(),y.type(),"정보 변경");
            changed(changes,name,"상태",x.status(),y.status(),"상태 변경");
            changed(changes,name,"효력시각",x.since(),y.since(),"시각 변경");
            var oldKeys = x.certificates().stream().map(Certificate::publicKey).distinct().sorted().toList();
            var newKeys = y.certificates().stream().map(Certificate::publicKey).distinct().sorted().toList();
            var oldCerts = x.certificates().stream().map(Certificate::fingerprint).sorted().toList();
            var newCerts = y.certificates().stream().map(Certificate::fingerprint).sorted().toList();
            if (!oldCerts.equals(newCerts)) changes.add(new Change(name,"신뢰점 인증서",x.certificates().size()+"장",y.certificates().size()+"장",
                    oldKeys.equals(newKeys) ? "같은 공개키 인증서 갱신" : "공개키 변경"));
            if (!x.timeline().equals(y.timeline())) changes.add(new Change(name,"상태 구간",periods(x.timeline()),periods(y.timeline()),"이력 변경"));
            if (!x.metadata().equals(y.metadata()) && x.timeline().equals(y.timeline()))
                changes.add(new Change(name,"명칭·확장·이력 상세","이전 XML","새 XML 확인","상세 변경"));
        }
        return List.copyOf(changes);
    }
    private static Map<String,List<Service>> group(List<Service> services) {
        var result = new LinkedHashMap<String,List<Service>>();
        for(var service:services) result.computeIfAbsent(service.key(),k->new ArrayList<>()).add(service);return result;
    }
    private static String periods(List<Period> values) { return values.stream().map(p -> p.status()+" · "+p.from()+" ~ "+(p.until()==null?"현재 구간":p.until())).collect(java.util.stream.Collectors.joining(" / ")); }
    private static void changed(List<Change> changes,String target,String field,Object before,Object after,String kind) {
        if(!Objects.equals(before,after))changes.add(new Change(target,field,String.valueOf(before),String.valueOf(after),kind));
    }
}
