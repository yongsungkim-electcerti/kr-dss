package com.electcerti.krdss.poc.rp.pdf;

import com.electcerti.krdss.tl.builder.KrTrustListXml;
import com.electcerti.krdss.tl.model.KrTrustList;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KrTrustListDocumentTest {
    @Test void existingDemoImportExplicitlySupportsMissingHistoryPeriod() {
        Instant start = Instant.parse("2026-10-09T00:00:00Z");
        var source = new KrTrustList(new KrTrustList.SchemeInformation(1, "KISA", start, start.plusSeconds(10)),
                List.of());
        String xml = new String(KrTrustListXml.toXml(source), StandardCharsets.UTF_8)
                .replace("<HistoricalInformationPeriod>65535</HistoricalInformationPeriod>", "");
        var document = KrTrustListDocument.parse(xml.getBytes(StandardCharsets.UTF_8));
        assertEquals(BigInteger.ONE, document.trustList().schemeInformation().sequenceNumber());
        assertEquals(6, document.trustList().schemeInformation().formatVersion());
    }

    @Test void reportDoesNotTruncateLargeSequenceNumbers() throws Exception {
        String sequence = "999999999999999999999999";
        var report = new PdfVerifyReport.TrustListInfo(true, null, "KISA", sequence,
                null, null, 0, null, null, "test");
        var mapper = new ObjectMapper();
        var json = mapper.readTree(mapper.writeValueAsBytes(report));
        assertTrue(json.get("version").isTextual());
        assertEquals(sequence, json.get("version").textValue());
    }
}
