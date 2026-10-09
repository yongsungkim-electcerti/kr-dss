package com.electcerti.krdss.tl.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 이미 수용한 스냅숏의 시점 조회. 서명·체인·정책 검증을 대신하지 않는다. */
public final class TrustServiceTimeline {
    private TrustServiceTimeline() { }

    public enum Reason { FOUND, NOT_ACCREDITED_AT_TIME, INVALID_HISTORY }
    public record Resolution(Reason reason, String statusUri, Instant effectiveFrom, Instant effectiveUntil,
            List<KrTrustList.ServiceExtension> extensions) {
        public Resolution { extensions = List.copyOf(extensions); }
    }

    /** 최신순·중복 시작 시각을 검사한 뒤 반열린 구간으로 조회한다. URI 해석은 호출자의 프로파일 책임이다. */
    public static Resolution resolve(KrTrustList.TrustService service, Instant time) {
        Objects.requireNonNull(service);
        Objects.requireNonNull(time);
        Instant later = service.current().statusStartingTime();
        for (var entry : service.history()) {
            if (!entry.statusStartingTime().isBefore(later)) {
                return new Resolution(Reason.INVALID_HISTORY, null, null, null, List.of());
            }
            later = entry.statusStartingTime();
        }
        var current = service.current();
        if (!time.isBefore(current.statusStartingTime())) {
            return new Resolution(Reason.FOUND, current.statusUri(), current.statusStartingTime(), null,
                    current.extensions());
        }
        Instant until = current.statusStartingTime();
        for (var entry : service.history()) {
            if (!time.isBefore(entry.statusStartingTime())) {
                return new Resolution(Reason.FOUND, entry.statusUri(), entry.statusStartingTime(), until,
                        entry.extensions());
            }
            until = entry.statusStartingTime();
        }
        return new Resolution(Reason.NOT_ACCREDITED_AT_TIME, null, null, null, List.of());
    }
}
