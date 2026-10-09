package com.electcerti.krdss.tl.model;

import com.electcerti.krdss.tl.model.KrTrustList.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrustServiceTimelineTest {
    private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");
    private static final Instant T1 = T0.plusSeconds(10), T2 = T0.plusSeconds(20);
    private static final List<LocalizedName> NAMES = List.of(new LocalizedName("ko", "시험 CA"));

    private TrustService service(List<ServiceHistoryInstance> history) {
        return new TrustService("service-1", new ServiceInformation("CA", NAMES,
                new CurrentDigitalIdentity(List.of(new BinaryValue(new byte[]{1})), List.of(), null),
                PocTrustListProfile.STATUS_ACCREDITED, T2, List.of()), history);
    }

    private ServiceHistoryInstance history(Instant start, String status) {
        return new ServiceHistoryInstance("CA", NAMES,
                new HistoricalDigitalIdentity(List.of(new BinaryValue(new byte[]{2})), null),
                status, start, List.of());
    }

    @Test void resolvesHalfOpenIntervalsAndKeepsSuspensionAfterRestoration() {
        var service = service(List.of(history(T1, PocTrustListProfile.STATUS_SUSPENDED),
                history(T0, PocTrustListProfile.STATUS_ACCREDITED)));
        assertEquals(TrustServiceTimeline.Reason.NOT_ACCREDITED_AT_TIME,
                TrustServiceTimeline.resolve(service, T0.minusNanos(1)).reason());
        assertEquals(PocTrustListProfile.STATUS_ACCREDITED, TrustServiceTimeline.resolve(service, T0).statusUri());
        assertEquals(PocTrustListProfile.STATUS_SUSPENDED, TrustServiceTimeline.resolve(service, T1).statusUri());
        assertEquals(T2, TrustServiceTimeline.resolve(service, T2.minusNanos(1)).effectiveUntil());
        assertEquals(PocTrustListProfile.STATUS_ACCREDITED, TrustServiceTimeline.resolve(service, T2).statusUri());
        assertNull(TrustServiceTimeline.resolve(service, T2).effectiveUntil());
    }

    @Test void invalidOrderingAndDuplicateBoundariesAreNotSortedAway() {
        for (var entries : List.of(
                List.of(history(T0, "a"), history(T1, "b")),
                List.of(history(T1, "a"), history(T1, "b")),
                List.of(history(T2, "a")))) {
            assertEquals(TrustServiceTimeline.Reason.INVALID_HISTORY,
                    TrustServiceTimeline.resolve(service(entries), T2).reason());
        }
    }

    @Test void unknownStatusIsPreservedRatherThanWithdrawn() {
        var service = service(List.of(history(T1, "urn:unknown:status")));
        var result = TrustServiceTimeline.resolve(service, T1);
        assertEquals("urn:unknown:status", result.statusUri());
        assertEquals(ServiceStatus.UNKNOWN, PocTrustListProfile.status(result.statusUri()));
        assertEquals(ServiceStatus.UNKNOWN, PocTrustListProfile.status(KrTrustList.LEGACY_GRANTED));
    }

    @Test void arraysAndListsAreDefensivelyCopied() {
        byte[] input = {1, 2};
        var value = new BinaryValue(input);
        input[0] = 9;
        value.bytes()[1] = 9;
        assertArrayEquals(new byte[]{1, 2}, value.bytes());
        assertEquals(new BinaryValue(new byte[]{1, 2}), value);
        var entries = new ArrayList<ServiceHistoryInstance>();
        entries.add(history(T1, "a"));
        var service = service(entries);
        entries.clear();
        assertEquals(1, service.history().size());
        assertThrows(UnsupportedOperationException.class, () -> service.history().clear());
    }

    @Test void profileRequiresLevelOnlyForCaAndRejectsLegacyInputStatus() {
        assertEquals(2, PocTrustListProfile.extensions(PocTrustListProfile.ServiceType.CA,
                PocTrustListProfile.TrustDomain.JOINT, PocTrustListProfile.TrustPointLevel.ROOT).size());
        assertEquals(1, PocTrustListProfile.extensions(PocTrustListProfile.ServiceType.OCSP,
                PocTrustListProfile.TrustDomain.SIMPLE, null).size());
        assertThrows(IllegalArgumentException.class, () -> PocTrustListProfile.extensions(
                PocTrustListProfile.ServiceType.TSA, PocTrustListProfile.TrustDomain.JOINT,
                PocTrustListProfile.TrustPointLevel.CA));
        assertThrows(IllegalArgumentException.class, () -> PocTrustListProfile.extensions(
                PocTrustListProfile.ServiceType.CA, PocTrustListProfile.TrustDomain.JOINT, null));
        assertThrows(IllegalArgumentException.class, () -> PocTrustListProfile.statusUri(ServiceStatus.GRANTED));
    }
}
