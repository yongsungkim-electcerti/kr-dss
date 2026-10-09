package com.electcerti.krdss.tl.builder;

import com.electcerti.krdss.tl.model.*;
import com.electcerti.krdss.tl.model.KrTrustList.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KrTrustListXmlTest {
    private static final Instant NOW = Instant.parse("2026-10-09T00:00:00Z");
    private static final List<LocalizedName> NAMES = List.of(
            new LocalizedName("ko", "시험 서비스"), new LocalizedName("en", "Test service"));

    static KrTrustList sample() {
        var id = new CurrentDigitalIdentity(List.of(new BinaryValue(new byte[]{1, 2}),
                new BinaryValue(new byte[]{3, 4})), List.of(new BinaryValue(new byte[]{5})), "CN=Test");
        var extensions = PocTrustListProfile.extensions(PocTrustListProfile.ServiceType.CA,
                PocTrustListProfile.TrustDomain.JOINT, PocTrustListProfile.TrustPointLevel.CA);
        var current = new ServiceInformation(PocTrustListProfile.serviceTypeUri(PocTrustListProfile.ServiceType.CA),
                NAMES, id, PocTrustListProfile.STATUS_ACCREDITED, NOW, extensions);
        var old = new ServiceHistoryInstance(current.serviceTypeUri(), List.of(new LocalizedName("ko", "이전 이름")),
                new HistoricalDigitalIdentity(List.of(new BinaryValue(new byte[]{5})), "CN=Test"),
                PocTrustListProfile.STATUS_SUSPENDED, NOW.minusSeconds(10), List.of());
        return new KrTrustList(new SchemeInformation(6, new BigInteger("999999999999999999999999"),
                "KISA PoC", NOW, NOW.plusSeconds(3600), 65535),
                List.of(new TrustServiceProvider("provider-internal", "사업자",
                        List.of(new TrustService("service-internal", current, List.of(old))))));
    }

    private static String xml(KrTrustList list) { return new String(KrTrustListXml.toXml(list), StandardCharsets.UTF_8); }
    private static KrTrustList parse(String xml) { return KrTrustListXml.fromXml(xml.getBytes(StandardCharsets.UTF_8)); }
    private static TrustService service(KrTrustList list) { return list.trustServiceProviders().get(0).services().get(0); }

    @Test void roundTripsIndependentVersionHugeSequenceIdentitiesHistoryAndNames() {
        String xml = xml(sample());
        KrTrustList parsed = parse(xml);
        assertEquals(sample().schemeInformation(), parsed.schemeInformation());
        var result = service(parsed);
        assertEquals(service(sample()).current().names(), result.current().names());
        assertEquals(service(sample()).current().digitalIdentity(), result.current().digitalIdentity());
        assertEquals(service(sample()).history(), result.history());
        assertEquals(2, result.current().extensions().size());
        assertTrue(result.current().extensions().get(0).critical());
        assertFalse(xml.contains("service-internal"));
        assertFalse(xml.contains("provider-internal"));
        String history = xml.substring(xml.indexOf("<ServiceHistory>"), xml.indexOf("</ServiceHistory>"));
        assertFalse(history.contains("X509Certificate"));
        assertTrue(history.contains("X509SKI"));
        assertEquals(6, parsed.schemeInformation().formatVersion());
    }

    @Test void preservesUnknownStatusWithoutConvertingToWithdrawn() {
        var parsed = parse(xml(sample()).replace(PocTrustListProfile.STATUS_ACCREDITED, "urn:test:future-status"));
        assertEquals("urn:test:future-status", service(parsed).current().statusUri());
        assertEquals(ServiceStatus.UNKNOWN, service(parsed).status());
        assertTrue(xml(parsed).contains("urn:test:future-status"));
    }

    @Test void semanticErrorsAreNotRepairedOrBlocked() {
        var original = sample();
        var scheme = new SchemeInformation(6, BigInteger.ONE, "KISA", NOW.plusSeconds(30), NOW.minusSeconds(20), 65535);
        var list = new KrTrustList(scheme, original.trustServiceProviders());
        assertEquals(scheme, parse(xml(list)).schemeInformation());
    }

    @Test void rejectsWrongNamespaceDuplicateFieldsInvalidTimesAndBadNumbers() {
        String xml = xml(sample());
        assertThrows(IllegalArgumentException.class, () -> parse(xml.replace(KrTrustListXml.NS, "urn:wrong")));
        assertThrows(IllegalArgumentException.class, () -> parse(xml.replace(
                "<TSLVersionIdentifier>6</TSLVersionIdentifier>",
                "<TSLVersionIdentifier>6</TSLVersionIdentifier><TSLVersionIdentifier>7</TSLVersionIdentifier>")));
        assertThrows(IllegalArgumentException.class, () -> parse(xml.replace("999999999999999999999999", "invalid")));
        assertThrows(IllegalArgumentException.class, () -> parse(xml.replace(NOW.toString(), "not-a-date")));
    }

    @Test void historyCertificatesAreRejectedAndMissingSkisCannotBeWritten() {
        String xml = xml(sample());
        int start = xml.indexOf("<ServiceHistory>");
        String invalid = xml.substring(0, start) + xml.substring(start).replace("X509SKI", "X509Certificate");
        assertThrows(IllegalArgumentException.class, () -> parse(invalid));
        var s = service(sample());
        var h = s.history().get(0);
        var badHistory = new ServiceHistoryInstance(h.serviceTypeUri(), h.names(),
                new HistoricalDigitalIdentity(List.of(), null), h.statusUri(), h.statusStartingTime(), h.extensions());
        var bad = new KrTrustList(sample().schemeInformation(),
                List.of(new TrustServiceProvider("p", List.of(new TrustService(null, s.current(), List.of(badHistory))))));
        assertThrows(IllegalArgumentException.class, () -> xml(bad));
    }

    @Test void noEmptyHistoryAndLegacyPeriodIsExplicit() {
        var s = service(sample());
        var noHistory = new KrTrustList(sample().schemeInformation(),
                List.of(new TrustServiceProvider("p", List.of(new TrustService(null, s.current(), List.of())))));
        assertFalse(xml(noHistory).contains("ServiceHistory"));
        String legacy = xml(noHistory).replace("<HistoricalInformationPeriod>65535</HistoricalInformationPeriod>", "");
        assertThrows(IllegalArgumentException.class, () -> parse(legacy));
        var converted = KrTrustListXml.fromLegacyXml(legacy.getBytes(StandardCharsets.UTF_8));
        assertEquals(sample().schemeInformation().sequenceNumber(), converted.schemeInformation().sequenceNumber());
        assertEquals(65535, converted.schemeInformation().historicalInformationPeriod());
    }

    @Test void preservesNestedUnknownCriticalExtensionAndInheritedNamespaces() {
        String xml = xml(sample()).replace("<TrustServiceStatusList ", "<TrustServiceStatusList xmlns:z=\"urn:future\" ");
        xml = xml.replace("</ServiceInformationExtensions>",
                "<Extension Critical=\"1\"><z:Policy kind=\"z:Rule\"><z:Value>one</z:Value></z:Policy></Extension>"
                + "</ServiceInformationExtensions>");
        var parsed = parse(xml);
        var extension = service(parsed).current().extensions().get(2);
        assertTrue(extension.critical());
        assertTrue(extension.payloadXml().get(0).contains("urn:future"));
        var twice = parse(xml(parsed));
        assertTrue(service(twice).current().extensions().get(2).payloadXml().get(0).contains("z:Rule"));
        assertTrue(service(twice).current().extensions().get(2).payloadXml().get(0).contains("one"));
    }

    @Test void rejectsDtdAndInvalidBase64InsteadOfSilentlyDiscardingBytes() {
        String xml = xml(sample());
        assertThrows(IllegalArgumentException.class, () -> parse(xml.replace("<TrustServiceStatusList",
                "<!DOCTYPE x [<!ENTITY xxe SYSTEM \"file:///should-not-read\">]><TrustServiceStatusList")));
        assertThrows(IllegalArgumentException.class, () -> parse(xml.replace("AQI=", "AQ!I=")));
    }

    @Test void currentJsonRoundTripsThroughExistingReader() throws Exception {
        byte[] json = KrTrustListBuilder.mapper().writeValueAsBytes(sample());
        assertEquals(sample(), SignedKrTrustList.parse(json).trustList());
    }

    @Test void legacyJsonConversionDoesNotGuessFromNumericMagnitude() {
        String json = """
                {"schemeInformation":{"version":42,"operatorName":"KISA","issueDate":"2026-10-09T00:00:00Z",
                "nextUpdate":"2026-10-10T00:00:00Z"},"trustServiceProviders":[{"name":"Provider",
                "services":[{"serviceTypeIdentifier":"CA/QC","serviceName":"CA","status":"GRANTED",
                "statusStartingTime":"2026-10-09T00:00:00Z","digitalIdentity":"AQI="}]}]}
                """;
        var parsed = SignedKrTrustList.parse(json.getBytes(StandardCharsets.UTF_8)).trustList();
        assertEquals(6, parsed.schemeInformation().formatVersion());
        assertEquals(BigInteger.valueOf(42), parsed.schemeInformation().sequenceNumber());
        assertEquals(ServiceStatus.GRANTED, service(parsed).status());
        assertTrue(service(parsed).history().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SignedKrTrustList.parse(
                json.replace("\"version\":42", "\"version\":42,\"formatVersion\":6").getBytes(StandardCharsets.UTF_8)));
    }
}
