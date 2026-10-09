package com.electcerti.krdss.poc.kisa.issuance;

import com.electcerti.krdss.poc.kisa.issuance.Issuance.Diagnostic;
import com.electcerti.krdss.poc.kisa.registry.RegistryDraft;
import com.electcerti.krdss.poc.kisa.registry.RegistryService;
import com.electcerti.krdss.tl.model.KrTrustList;
import com.electcerti.krdss.tl.model.KrTrustList.HistoricalDigitalIdentity;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceHistoryInstance;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceInformation;
import com.electcerti.krdss.tl.model.KrTrustList.TrustService;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 발행 후보 편성과 정상 규칙 사전 검사.
 *
 * <p>정상 기준(BASELINE) 스냅숏과 비교해 서비스의 최종 변경만 이력으로 넣는다. 인증서 추가(같은 키 갱신)만
 * 있으면 이력을 만들지 않는다. 입력을 정상값으로 고치지 않고 진단으로 남긴다.</p>
 */
final class CandidateBuilder {

    static final Duration FUTURE_TOLERANCE = Duration.ofMinutes(5);
    /** ETSI TS 119 612: NextUpdate는 발행 후 6개월을 넘지 않는다. */
    static final long MAX_NEXT_UPDATE_MONTHS = 6;

    private CandidateBuilder() {
    }

    record Candidate(KrTrustList list, List<Diagnostic> diagnostics) {
    }

    /** 기준 정보: 마지막 BASELINE 스냅숏과 그 순번·발행 시각. 최초 발행이면 모두 null. */
    record Baseline(KrTrustList snapshot, BigInteger sequence, Instant issuedAt) {
        static final Baseline NONE = new Baseline(null, null, null);
    }

    static BigInteger recommendedSequence(Baseline baseline) {
        return baseline.sequence() == null ? BigInteger.ONE : baseline.sequence().add(BigInteger.ONE);
    }

    static Candidate build(RegistryDraft draft, Baseline baseline, BigInteger sequence, Instant issuedAt,
            Instant nextUpdate, Instant now) {
        var diagnostics = new ArrayList<Diagnostic>();
        checkScheme(baseline, sequence, issuedAt, nextUpdate, now, diagnostics);

        Map<String, TrustService> previous = new HashMap<>();
        if (baseline.snapshot() != null) {
            for (var provider : baseline.snapshot().trustServiceProviders()) {
                for (var service : provider.services()) previous.put(service.serviceId(), service);
            }
        }
        var providers = new ArrayList<KrTrustList.TrustServiceProvider>();
        for (var provider : draft.providers()) {
            var services = new ArrayList<TrustService>();
            for (var record : provider.services()) {
                var current = RegistryService.serviceInformation(record);
                var before = previous.get(record.serviceId());
                services.add(withHistory(record.serviceId(), record.name(), current, before, baseline, diagnostics));
            }
            providers.add(new KrTrustList.TrustServiceProvider(provider.providerId(), provider.name(), services));
        }
        var list = new KrTrustList(RegistryService.scheme(draft, sequence, issuedAt, nextUpdate), providers);
        return new Candidate(list, List.copyOf(diagnostics));
    }

    private static TrustService withHistory(String serviceId, String label, ServiceInformation current,
            TrustService before, Baseline baseline, List<Diagnostic> diagnostics) {
        if (before == null) {
            if (baseline.issuedAt() != null && current.statusStartingTime().isBefore(baseline.issuedAt())) {
                diagnostics.add(new Diagnostic("STATUS_START_RETROACTIVE", serviceId,
                        label + ": 새 서비스의 상태 시작이 직전 정상 발행 시각보다 이릅니다."));
            }
            return new TrustService(serviceId, current, List.of());
        }
        var old = before.current();
        if (sameExceptCertificates(old, current)) {
            // 같은 키 인증서 갱신·단순 재발행은 이력을 추가하지 않는다.
            return new TrustService(serviceId, current, before.history());
        }
        if (!current.statusStartingTime().isAfter(old.statusStartingTime())) {
            diagnostics.add(new Diagnostic("HISTORY_START_NOT_INCREASING", serviceId,
                    label + ": 변경된 정보의 시작 시각이 직전 구간 시작보다 늦지 않습니다."));
        }
        if (baseline.issuedAt() != null && current.statusStartingTime().isBefore(baseline.issuedAt())) {
            diagnostics.add(new Diagnostic("STATUS_START_RETROACTIVE", serviceId,
                    label + ": 변경 효력 시각이 직전 정상 발행 시각보다 이릅니다(소급)."));
        }
        var history = new ArrayList<ServiceHistoryInstance>();
        history.add(new ServiceHistoryInstance(old.serviceTypeUri(), old.names(),
                new HistoricalDigitalIdentity(old.digitalIdentity().subjectKeyIdentifiers(),
                        old.digitalIdentity().subjectName()),
                old.statusUri(), old.statusStartingTime(), old.extensions()));
        history.addAll(before.history());
        return new TrustService(serviceId, current, history);
    }

    private static boolean sameExceptCertificates(ServiceInformation a, ServiceInformation b) {
        return a.serviceTypeUri().equals(b.serviceTypeUri()) && a.names().equals(b.names())
                && a.statusUri().equals(b.statusUri()) && a.statusStartingTime().equals(b.statusStartingTime())
                && a.extensions().equals(b.extensions())
                && Objects.equals(a.digitalIdentity().subjectName(), b.digitalIdentity().subjectName());
    }

    private static void checkScheme(Baseline baseline, BigInteger sequence, Instant issuedAt, Instant nextUpdate,
            Instant now, List<Diagnostic> diagnostics) {
        var recommended = recommendedSequence(baseline);
        if (baseline.sequence() != null && sequence.compareTo(baseline.sequence()) <= 0) {
            diagnostics.add(new Diagnostic("SEQUENCE_NOT_INCREASING", "sequenceNumber",
                    "순번이 직전 정상 순번(" + baseline.sequence() + ") 이하입니다."));
        } else if (sequence.compareTo(recommended) > 0) {
            diagnostics.add(new Diagnostic("SEQUENCE_GAP", "sequenceNumber",
                    "순번이 권장값(" + recommended + ")보다 커서 중간 순번이 비게 됩니다."));
        }
        if (baseline.issuedAt() != null && !issuedAt.isAfter(baseline.issuedAt())) {
            diagnostics.add(new Diagnostic("ISSUE_TIME_NOT_INCREASING", "issuedAt",
                    "발행 시각이 직전 정상 발행 시각(" + baseline.issuedAt() + ") 이후가 아닙니다."));
        }
        if (issuedAt.isAfter(now.plus(FUTURE_TOLERANCE))) {
            diagnostics.add(new Diagnostic("ISSUE_TIME_IN_FUTURE", "issuedAt", "발행 시각이 현재보다 미래입니다."));
        }
        if (!nextUpdate.isAfter(issuedAt)) {
            diagnostics.add(new Diagnostic("NEXT_UPDATE_NOT_AFTER_ISSUE", "nextUpdate",
                    "NextUpdate가 발행 시각 이후가 아닙니다."));
        } else if (nextUpdate.isAfter(issuedAt.atZone(ZoneOffset.UTC)
                .plusMonths(MAX_NEXT_UPDATE_MONTHS).toInstant())) {
            diagnostics.add(new Diagnostic("NEXT_UPDATE_TOO_FAR", "nextUpdate",
                    "NextUpdate가 발행 후 6개월을 넘습니다."));
        }
        if (nextUpdate.isBefore(now) && nextUpdate.isAfter(issuedAt)) {
            diagnostics.add(new Diagnostic("NEXT_UPDATE_PASSED", "nextUpdate", "NextUpdate가 이미 지났습니다."));
        }
    }

    static Instant defaultIssuedAt(Instant now) {
        return now.truncatedTo(ChronoUnit.SECONDS);
    }
}
