package com.electcerti.krdss.tl.builder;

import com.electcerti.krdss.tl.model.KrTrustList;
import com.electcerti.krdss.tl.model.KrTrustList.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.*;

/**
 * 모델이 지원하는 TL 정보의 XML 변환. 전체 ETSI XSD 검증이나 서명 검증을 대신하지 않는다.
 * 서명 원문은 재직렬화하지 않고 별도로 보관·검증해야 한다.
 */
public final class KrTrustListXml {
    public static final String NS = "http://uri.etsi.org/02231/v2#";
    private KrTrustListXml() { }

    public static boolean looksLikeXml(byte[] document) {
        if (document == null) return false;
        int i = document.length >= 3 && (document[0] & 255) == 239
                && (document[1] & 255) == 187 && (document[2] & 255) == 191 ? 3 : 0;
        while (i < document.length && Character.isWhitespace(document[i])) i++;
        return i < document.length && document[i] == '<';
    }

    public static byte[] toXml(KrTrustList list) {
        Document doc = newDocument();
        Element root = doc.createElementNS(NS, "TrustServiceStatusList");
        root.setAttribute("Id", "kr-tl");
        root.setAttribute("TSLTag", "http://uri.etsi.org/19612/TSLTag");
        doc.appendChild(root);
        var scheme = list.schemeInformation();
        Element si = child(doc, root, "SchemeInformation");
        text(doc, si, "TSLVersionIdentifier", Integer.toString(scheme.formatVersion()));
        text(doc, si, "TSLSequenceNumber", scheme.sequenceNumber().toString());
        names(doc, si, "SchemeOperatorName", List.of(new LocalizedName("ko", scheme.operatorName())));
        text(doc, si, "HistoricalInformationPeriod", Integer.toString(scheme.historicalInformationPeriod()));
        text(doc, si, "ListIssueDateTime", scheme.issueDate().toString());
        text(doc, child(doc, si, "NextUpdate"), "dateTime", scheme.nextUpdate().toString());
        Element providers = child(doc, root, "TrustServiceProviderList");
        for (var provider : list.trustServiceProviders()) {
            Element pe = child(doc, providers, "TrustServiceProvider");
            names(doc, child(doc, pe, "TSPInformation"), "TSPName",
                    List.of(new LocalizedName("ko", provider.name())));
            Element services = child(doc, pe, "TSPServices");
            for (var service : provider.services()) {
                Element se = child(doc, services, "TSPService");
                var current = service.current();
                Element info = child(doc, se, "ServiceInformation");
                prefix(doc, info, current.serviceTypeUri(), current.names());
                var id = current.digitalIdentity();
                if (id.certificates().isEmpty()) throw invalid("Current identity requires a certificate");
                identity(doc, info, id.certificates(), id.subjectKeyIdentifiers(), id.subjectName());
                suffix(doc, info, current.statusUri(), current.statusStartingTime(), current.extensions());
                if (!service.history().isEmpty()) {
                    Element history = child(doc, se, "ServiceHistory");
                    for (var previous : service.history()) {
                        Element entry = child(doc, history, "ServiceHistoryInstance");
                        prefix(doc, entry, previous.serviceTypeUri(), previous.names());
                        var hid = previous.digitalIdentity();
                        if (hid.subjectKeyIdentifiers().isEmpty()) throw invalid("History requires X509SKI");
                        identity(doc, entry, List.of(), hid.subjectKeyIdentifiers(), hid.subjectName());
                        suffix(doc, entry, previous.statusUri(), previous.statusStartingTime(), previous.extensions());
                    }
                }
            }
        }
        return serialize(doc, false);
    }

    public static KrTrustList fromXml(byte[] xml) {
        return read(xml, false);
    }

    /** 이전 시연 XML의 이력 보존 기간 누락만 허용한다. 버전과 순번은 여전히 각각 읽는다. */
    public static KrTrustList fromLegacyXml(byte[] xml) {
        return read(xml, true);
    }

    private static KrTrustList read(byte[] xml, boolean legacy) {
        Element root = parse(xml).getDocumentElement();
        if (!NS.equals(root.getNamespaceURI()) || !"TrustServiceStatusList".equals(root.getLocalName())) {
            throw invalid("Expected a TL root in the TL namespace");
        }
        Element si = required(root, "SchemeInformation");
        String period = optional(si, "HistoricalInformationPeriod");
        if (period == null && !legacy) throw invalid("Missing HistoricalInformationPeriod");
        var scheme = new SchemeInformation(integer(si, "TSLVersionIdentifier"),
                new BigInteger(value(si, "TSLSequenceNumber")), value(required(si, "SchemeOperatorName"), "Name"),
                time(si, "ListIssueDateTime"), time(required(si, "NextUpdate"), "dateTime"),
                period == null ? 65535 : Integer.parseInt(period));
        List<TrustServiceProvider> providers = new ArrayList<>();
        for (Element pe : children(required(root, "TrustServiceProviderList"), "TrustServiceProvider")) {
            String name = value(required(required(pe, "TSPInformation"), "TSPName"), "Name");
            List<TrustService> services = new ArrayList<>();
            for (Element se : children(required(pe, "TSPServices"), "TSPService")) {
                Element ci = required(se, "ServiceInformation");
                var cid = readIdentity(required(ci, "ServiceDigitalIdentity"), false);
                var current = new ServiceInformation(value(ci, "ServiceTypeIdentifier"), readNames(ci),
                        new CurrentDigitalIdentity(cid.certificates(), cid.skis(), cid.subject()),
                        value(ci, "ServiceStatus"), time(ci, "StatusStartingTime"), readExtensions(ci));
                List<ServiceHistoryInstance> history = new ArrayList<>();
                Element he = one(se, "ServiceHistory");
                if (he != null) for (Element hi : children(he, "ServiceHistoryInstance")) {
                    var hid = readIdentity(required(hi, "ServiceDigitalIdentity"), true);
                    history.add(new ServiceHistoryInstance(value(hi, "ServiceTypeIdentifier"), readNames(hi),
                            new HistoricalDigitalIdentity(hid.skis(), hid.subject()),
                            value(hi, "ServiceStatus"), time(hi, "StatusStartingTime"), readExtensions(hi)));
                }
                // 관리 ID는 XML에 추가하지 않는다. 가져온 서비스의 연결은 관리 저장소 책임이다.
                services.add(new TrustService(null, current, history));
            }
            providers.add(new TrustServiceProvider(null, name, services));
        }
        return new KrTrustList(scheme, providers);
    }

    private record Identity(List<BinaryValue> certificates, List<BinaryValue> skis, String subject) { }

    private static Identity readIdentity(Element parent, boolean historical) {
        List<BinaryValue> certs = new ArrayList<>(), skis = new ArrayList<>();
        String subject = null;
        for (Element id : children(parent, "DigitalId")) {
            for (Element element : elements(id)) {
                if (!NS.equals(element.getNamespaceURI())) throw invalid("Foreign DigitalId element");
                String v = element.getTextContent().trim();
                switch (element.getLocalName()) {
                    case "X509Certificate" -> {
                        if (historical) throw invalid("Certificate in historical identity");
                        certs.add(binary(v));
                    }
                    case "X509SKI" -> skis.add(binary(v));
                    case "X509SubjectName" -> {
                        if (subject != null) throw invalid("Duplicate subject name");
                        subject = v;
                    }
                    default -> throw invalid("Unsupported DigitalId: " + element.getLocalName());
                }
            }
        }
        if (historical ? skis.isEmpty() : certs.isEmpty()) throw invalid("Incomplete digital identity");
        return new Identity(certs, skis, subject);
    }

    private static BinaryValue binary(String value) {
        // XML base64Binary permits whitespace, but arbitrary non-alphabet bytes must not be ignored.
        return new BinaryValue(Base64.getDecoder().decode(value.replaceAll("[\\t\\n\\r ]", "")));
    }

    private static void prefix(Document doc, Element info, String type, List<LocalizedName> names) {
        text(doc, info, "ServiceTypeIdentifier", type);
        names(doc, info, "ServiceName", names);
    }

    private static void suffix(Document doc, Element info, String status, Instant start,
            List<ServiceExtension> extensions) {
        text(doc, info, "ServiceStatus", status);
        text(doc, info, "StatusStartingTime", start.toString());
        if (!extensions.isEmpty()) {
            Element container = child(doc, info, "ServiceInformationExtensions");
            for (var extension : extensions) {
                Element e = child(doc, container, "Extension");
                e.setAttribute("Critical", Boolean.toString(extension.critical()));
                for (String payload : extension.payloadXml()) {
                    Element parsed = parse(payload.getBytes(StandardCharsets.UTF_8)).getDocumentElement();
                    e.appendChild(doc.importNode(parsed, true));
                }
            }
        }
    }

    private static List<ServiceExtension> readExtensions(Element info) {
        Element container = one(info, "ServiceInformationExtensions");
        if (container == null) return List.of();
        List<ServiceExtension> result = new ArrayList<>();
        for (Element extension : children(container, "Extension")) {
            String critical = extension.getAttribute("Critical");
            if (!List.of("true", "false", "1", "0").contains(critical)) throw invalid("Invalid Critical");
            List<String> payloads = new ArrayList<>();
            for (Element payload : elements(extension)) payloads.add(fragment(payload));
            result.add(new ServiceExtension(critical.equals("true") || critical.equals("1"), payloads));
        }
        return List.copyOf(result);
    }

    /** 상위 namespace 선언을 보존하여 미지원 QName 속성의 접두사를 잃지 않는다. */
    private static String fragment(Element element) {
        Document doc = newDocument();
        Element clone = (Element) doc.importNode(element, true);
        for (Node node = element; node instanceof Element; node = node.getParentNode()) {
            var attributes = node.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                var attr = attributes.item(i);
                if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attr.getNamespaceURI())
                        && !clone.hasAttribute(attr.getNodeName())) {
                    clone.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, attr.getNodeName(), attr.getNodeValue());
                }
            }
        }
        doc.appendChild(clone);
        return new String(serialize(doc, true), StandardCharsets.UTF_8);
    }

    private static void identity(Document doc, Element info, List<BinaryValue> certs,
            List<BinaryValue> skis, String subject) {
        Element parent = child(doc, info, "ServiceDigitalIdentity");
        for (var cert : certs) text(doc, child(doc, parent, "DigitalId"), "X509Certificate",
                Base64.getEncoder().encodeToString(cert.bytes()));
        if (subject != null) text(doc, child(doc, parent, "DigitalId"), "X509SubjectName", subject);
        for (var ski : skis) text(doc, child(doc, parent, "DigitalId"), "X509SKI",
                Base64.getEncoder().encodeToString(ski.bytes()));
    }

    private static void names(Document doc, Element parent, String tag, List<LocalizedName> names) {
        if (names.isEmpty()) throw invalid("Empty names");
        Element container = child(doc, parent, tag);
        for (var name : names) {
            Element e = child(doc, container, "Name");
            e.setAttributeNS(XMLConstants.XML_NS_URI, "xml:lang", name.language());
            e.setTextContent(name.value());
        }
    }

    private static List<LocalizedName> readNames(Element info) {
        List<LocalizedName> result = new ArrayList<>();
        for (Element n : children(required(info, "ServiceName"), "Name")) {
            result.add(new LocalizedName(n.getAttributeNS(XMLConstants.XML_NS_URI, "lang"), n.getTextContent()));
        }
        if (result.isEmpty()) throw invalid("Missing ServiceName/Name");
        return List.copyOf(result);
    }

    private static Element child(Document d, Element parent, String name) {
        Element e = d.createElementNS(NS, name);
        parent.appendChild(e);
        return e;
    }
    private static void text(Document d, Element p, String name, String value) {
        child(d, p, name).setTextContent(value);
    }
    private static List<Element> elements(Element parent) {
        List<Element> result = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e) result.add(e);
        }
        return result;
    }
    private static List<Element> children(Element parent, String name) {
        return elements(parent).stream().filter(e -> NS.equals(e.getNamespaceURI()) && name.equals(e.getLocalName())).toList();
    }
    private static Element one(Element parent, String name) {
        var found = children(parent, name);
        if (found.size() > 1) throw invalid("Duplicate " + name);
        return found.isEmpty() ? null : found.get(0);
    }
    private static Element required(Element parent, String name) {
        Element e = one(parent, name);
        if (e == null) throw invalid("Missing " + name);
        return e;
    }
    private static String value(Element parent, String name) {
        String value = required(parent, name).getTextContent().trim();
        if (value.isEmpty()) throw invalid("Empty " + name);
        return value;
    }
    private static String optional(Element parent, String name) {
        Element e = one(parent, name);
        return e == null ? null : e.getTextContent().trim();
    }
    private static int integer(Element parent, String name) { return Integer.parseInt(value(parent, name)); }
    private static Instant time(Element parent, String name) {
        try { return Instant.parse(value(parent, name)); }
        catch (java.time.DateTimeException e) { throw new IllegalArgumentException("Invalid " + name, e); }
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }

    private static DocumentBuilderFactory factory() {
        var f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        try {
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            f.setXIncludeAware(false);
            f.setExpandEntityReferences(false);
            return f;
        } catch (Exception e) { throw new IllegalStateException("Secure XML parser unavailable", e); }
    }
    private static Document newDocument() {
        try { return factory().newDocumentBuilder().newDocument(); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static Document parse(byte[] bytes) {
        try { return factory().newDocumentBuilder().parse(new ByteArrayInputStream(bytes)); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid TL XML", e); }
    }
    private static byte[] serialize(Document doc, boolean fragment) {
        try {
            var f = TransformerFactory.newInstance();
            f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var t = f.newTransformer();
            t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            t.setOutputProperty(OutputKeys.INDENT, "no");
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, fragment ? "yes" : "no");
            var output = new ByteArrayOutputStream();
            t.transform(new DOMSource(doc), new StreamResult(output));
            return output.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("Cannot serialize TL XML", e); }
    }
}
