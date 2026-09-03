package com.electcerti.krdss.tl.builder;

import com.electcerti.krdss.tl.model.KrTrustList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * KR-TL ↔ XML 변환 — ETSI TS 119 612 {@code TrustServiceStatusList} 구조를 따른다.
 *
 * <p>EU TL 과 같은 XML 골격을 쓰므로 기존 TL 도구·검증기가 읽을 수 있고, 서명도 EU TL 과
 * 동일하게 XAdES enveloped 로 붙일 수 있다. 다만 본 PoC 는 스키마 전체가 아니라 KR-TL
 * 도메인 모델이 담는 항목만 직렬화한다.</p>
 *
 * <p>서명은 이 클래스가 하지 않는다. 생성된 XML 에 XAdES enveloped 서명을 붙이는 일은
 * EU DSS 를 쓰는 상위 계층의 몫이다.</p>
 */
public final class KrTrustListXml {

    /** ETSI TS 119 612 신뢰목록 네임스페이스. */
    public static final String NS = "http://uri.etsi.org/02231/v2#";

    private static final String STATUS_BASE = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/";
    private static final String STATUS_GRANTED = STATUS_BASE + "granted";
    private static final String STATUS_WITHDRAWN = STATUS_BASE + "withdrawn";
    /**
     * TS 119 612 에는 "정지" 상태 URI 가 없다. 국내 인정제도의 효력정지를 표현해야 하므로
     * KR 확장 URI 를 쓴다. 표준 URI 와 섞이지 않도록 네임스페이스를 분리했다.
     */
    private static final String STATUS_SUSPENDED = "urn:kr:krdss:TrustedList:Svcstatus:suspended";

    private KrTrustListXml() {
    }

    /** 바이트가 XML 문서로 보이는지 — 앞쪽 공백·BOM 을 건너뛰고 여는 꺾쇠(&lt;)인지만 본다. */
    public static boolean looksLikeXml(byte[] document) {
        if (document == null) {
            return false;
        }
        int index = 0;
        // UTF-8 BOM
        if (document.length >= 3 && (document[0] & 0xFF) == 0xEF
                && (document[1] & 0xFF) == 0xBB && (document[2] & 0xFF) == 0xBF) {
            index = 3;
        }
        while (index < document.length && Character.isWhitespace(document[index])) {
            index++;
        }
        return index < document.length && document[index] == '<';
    }

    /** KR-TL 을 TS 119 612 XML 로 직렬화한다. */
    public static byte[] toXml(KrTrustList trustList) {
        Document document = newDocument();
        Element root = document.createElementNS(NS, "TrustServiceStatusList");
        root.setAttribute("Id", "kr-tl");
        root.setAttribute("TSLTag", "http://uri.etsi.org/19612/TSLTag");
        document.appendChild(root);

        KrTrustList.SchemeInformation scheme = trustList.schemeInformation();
        Element schemeInformation = child(document, root, "SchemeInformation");
        if (scheme != null) {
            text(document, schemeInformation, "TSLVersionIdentifier", String.valueOf(scheme.version()));
            text(document, schemeInformation, "TSLSequenceNumber", String.valueOf(scheme.version()));
            Element operatorName = child(document, schemeInformation, "SchemeOperatorName");
            name(document, operatorName, scheme.operatorName());
            text(document, schemeInformation, "ListIssueDateTime", format(scheme.issueDate()));
            Element nextUpdate = child(document, schemeInformation, "NextUpdate");
            text(document, nextUpdate, "dateTime", format(scheme.nextUpdate()));
        }

        Element providerList = child(document, root, "TrustServiceProviderList");
        if (trustList.trustServiceProviders() != null) {
            for (KrTrustList.TrustServiceProvider provider : trustList.trustServiceProviders()) {
                Element providerElement = child(document, providerList, "TrustServiceProvider");
                Element tspInformation = child(document, providerElement, "TSPInformation");
                Element tspName = child(document, tspInformation, "TSPName");
                name(document, tspName, provider.name());

                Element services = child(document, providerElement, "TSPServices");
                if (provider.services() == null) {
                    continue;
                }
                for (KrTrustList.TrustService service : provider.services()) {
                    Element serviceElement = child(document, services, "TSPService");
                    Element info = child(document, serviceElement, "ServiceInformation");
                    text(document, info, "ServiceTypeIdentifier", service.serviceTypeIdentifier());
                    Element serviceName = child(document, info, "ServiceName");
                    name(document, serviceName, service.serviceName());

                    Element identity = child(document, info, "ServiceDigitalIdentity");
                    Element digitalId = child(document, identity, "DigitalId");
                    if (service.digitalIdentity() != null) {
                        text(document, digitalId, "X509Certificate",
                                Base64.getEncoder().encodeToString(service.digitalIdentity()));
                    }
                    text(document, info, "ServiceStatus", statusUri(service.status()));
                    text(document, info, "StatusStartingTime", format(service.statusStartingTime()));
                }
            }
        }
        return serialize(document);
    }

    /**
     * TS 119 612 XML 을 KR-TL 모델로 되돌린다.
     *
     * <p>{@code ds:Signature} 는 무시한다 — 서명 검증은 XML 원문 위에서 별도로 수행한다.</p>
     */
    public static KrTrustList fromXml(byte[] xml) {
        Document document = parse(xml);
        Element root = document.getDocumentElement();
        if (root == null || !"TrustServiceStatusList".equals(root.getLocalName())) {
            throw new IllegalArgumentException(
                    "TS 119 612 신뢰목록 XML 이 아닙니다(루트 요소가 TrustServiceStatusList 가 아님).");
        }

        Element schemeElement = first(root, "SchemeInformation");
        KrTrustList.SchemeInformation scheme = null;
        if (schemeElement != null) {
            Element nextUpdate = first(schemeElement, "NextUpdate");
            scheme = new KrTrustList.SchemeInformation(
                    intOf(textOf(first(schemeElement, "TSLVersionIdentifier"))),
                    nameOf(first(schemeElement, "SchemeOperatorName")),
                    instantOf(textOf(first(schemeElement, "ListIssueDateTime"))),
                    instantOf(nextUpdate != null ? textOf(first(nextUpdate, "dateTime")) : null));
        }

        List<KrTrustList.TrustServiceProvider> providers = new ArrayList<>();
        Element providerList = first(root, "TrustServiceProviderList");
        if (providerList != null) {
            for (Element providerElement : all(providerList, "TrustServiceProvider")) {
                Element tspInformation = first(providerElement, "TSPInformation");
                String providerName = tspInformation != null ? nameOf(first(tspInformation, "TSPName")) : null;

                List<KrTrustList.TrustService> services = new ArrayList<>();
                Element servicesElement = first(providerElement, "TSPServices");
                if (servicesElement != null) {
                    for (Element serviceElement : all(servicesElement, "TSPService")) {
                        Element info = first(serviceElement, "ServiceInformation");
                        if (info == null) {
                            continue;
                        }
                        Element identity = first(info, "ServiceDigitalIdentity");
                        Element digitalId = identity != null ? first(identity, "DigitalId") : null;
                        String certificate = digitalId != null ? textOf(first(digitalId, "X509Certificate")) : null;

                        services.add(new KrTrustList.TrustService(
                                textOf(first(info, "ServiceTypeIdentifier")),
                                nameOf(first(info, "ServiceName")),
                                statusOf(textOf(first(info, "ServiceStatus"))),
                                instantOf(textOf(first(info, "StatusStartingTime"))),
                                certificate != null ? Base64.getDecoder().decode(certificate.trim()) : null));
                    }
                }
                providers.add(new KrTrustList.TrustServiceProvider(providerName, List.copyOf(services)));
            }
        }
        return new KrTrustList(scheme, List.copyOf(providers));
    }

    // --- 상태 매핑 ---------------------------------------------------------

    private static String statusUri(KrTrustList.ServiceStatus status) {
        if (status == null) {
            return STATUS_WITHDRAWN;
        }
        return switch (status) {
            case GRANTED -> STATUS_GRANTED;
            case WITHDRAWN -> STATUS_WITHDRAWN;
            case SUSPENDED -> STATUS_SUSPENDED;
        };
    }

    private static KrTrustList.ServiceStatus statusOf(String uri) {
        if (uri == null) {
            return KrTrustList.ServiceStatus.WITHDRAWN;
        }
        String value = uri.trim();
        if (STATUS_GRANTED.equals(value)) {
            return KrTrustList.ServiceStatus.GRANTED;
        }
        if (STATUS_SUSPENDED.equals(value)) {
            return KrTrustList.ServiceStatus.SUSPENDED;
        }
        return KrTrustList.ServiceStatus.WITHDRAWN;
    }

    // --- DOM 도우미 --------------------------------------------------------

    private static Element child(Document document, Element parent, String name) {
        Element element = document.createElementNS(NS, name);
        parent.appendChild(element);
        return element;
    }

    private static void text(Document document, Element parent, String name, String value) {
        Element element = child(document, parent, name);
        element.setTextContent(value != null ? value : "");
    }

    /** TS 119 612 의 다국어 이름 구조: {@code <Name xml:lang="ko">…</Name>}. */
    private static void name(Document document, Element parent, String value) {
        Element element = child(document, parent, "Name");
        element.setAttributeNS(XMLConstants.XML_NS_URI, "xml:lang", "ko");
        element.setTextContent(value != null ? value : "");
    }

    private static String nameOf(Element parent) {
        Element name = parent != null ? first(parent, "Name") : null;
        return textOf(name);
    }

    private static Element first(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS(NS, localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            // getElementsByTagNameNS 는 후손 전체를 훑으므로 직계 자식만 고른다.
            if (node.getParentNode() == parent) {
                return (Element) node;
            }
        }
        return null;
    }

    private static List<Element> all(Element parent, String localName) {
        List<Element> elements = new ArrayList<>();
        NodeList nodes = parent.getElementsByTagNameNS(NS, localName);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getParentNode() == parent) {
                elements.add((Element) node);
            }
        }
        return elements;
    }

    private static String textOf(Element element) {
        return element != null ? element.getTextContent() : null;
    }

    private static int intOf(String value) {
        try {
            return value != null ? Integer.parseInt(value.trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String format(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private static Instant instantOf(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // --- 안전한 파서·직렬화기 ---------------------------------------------

    private static DocumentBuilderFactory secureFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            // 외부 엔티티·DTD 를 막는다. 신뢰목록은 외부에서 받아오는 문서다.
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        } catch (Exception e) {
            throw new IllegalStateException("XML 파서를 안전 모드로 설정하지 못했습니다: " + e.getMessage(), e);
        }
        return factory;
    }

    private static Document newDocument() {
        try {
            DocumentBuilder builder = secureFactory().newDocumentBuilder();
            return builder.newDocument();
        } catch (Exception e) {
            throw new IllegalStateException("XML 문서를 만들지 못했습니다: " + e.getMessage(), e);
        }
    }

    private static Document parse(byte[] xml) {
        try {
            DocumentBuilder builder = secureFactory().newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (Exception e) {
            throw new IllegalArgumentException("KR-TL XML 파싱 실패: " + e.getMessage(), e);
        }
    }

    private static byte[] serialize(Document document) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.name());
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(out));
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("KR-TL XML 직렬화 실패: " + e.getMessage(), e);
        }
    }
}
