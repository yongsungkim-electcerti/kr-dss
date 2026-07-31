package com.electcerti.krdss.poc.kisa;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * KR-TL 운영 현황을 제공하는 PoC 관리 API.
 *
 * <p>실제 저장소와 전자서명 파이프라인이 연결되기 전까지 운영 화면 검증용
 * 인메모리 데이터를 제공한다.</p>
 */
@RestController
@RequestMapping("/api/admin")
public class KrTlAdminController {

    private final AtomicInteger sequence = new AtomicInteger(42);
    private final AtomicReference<Instant> publishedAt =
            new AtomicReference<>(Instant.now().minus(2, ChronoUnit.HOURS));

    @GetMapping("/dashboard")
    public Dashboard dashboard() {
        Instant lastPublishedAt = publishedAt.get();
        return new Dashboard(
                new Summary(12, 28, 25, 2, 1),
                new Distribution(18, 5, 3, 2),
                List.of(
                        new Provider("한국전자인증", "CA · OCSP", "정상", "2026-07-31 09:18", 100),
                        new Provider("코스콤 인증센터", "CA · TSA", "정상", "2026-07-31 09:12", 100),
                        new Provider("금융결제원", "CA · OCSP · TSA", "검토 필요", "2026-07-31 08:54", 86),
                        new Provider("한국정보인증", "CA · OCSP", "정상", "2026-07-31 08:41", 100),
                        new Provider("가상 원격서명 사업자", "RSSP · HSM", "일시 중지", "2026-07-30 17:20", 72)
                ),
                List.of(
                        new Activity("KR-TL #" + sequence.get() + " 배포 완료", "전자서명 검증 후 배포 채널 반영", "2시간 전", "success"),
                        new Activity("금융결제원 TSA 인증서 검토", "만료 예정 인증서 1건 발견", "47분 전", "warning"),
                        new Activity("사업자 신뢰정보 동기화", "12개 사업자 응답 · 오류 없음", "12분 전", "info")
                ),
                new Publication("KR-TL-" + sequence.get(), lastPublishedAt, lastPublishedAt.plus(24, ChronoUnit.HOURS),
                        "SHA-256", "서명 유효")
        );
    }

    @PostMapping("/publish")
    public Publication publish() {
        int nextSequence = sequence.incrementAndGet();
        Instant now = Instant.now();
        publishedAt.set(now);
        return new Publication("KR-TL-" + nextSequence, now, now.plus(24, ChronoUnit.HOURS),
                "SHA-256", "서명 유효");
    }

    public record Dashboard(
            Summary summary,
            Distribution distribution,
            List<Provider> providers,
            List<Activity> activities,
            Publication publication
    ) {
    }

    public record Summary(int providers, int services, int granted, int suspended, int reviewRequired) {
    }

    public record Distribution(int certificateAuthorities, int timestampAuthorities, int ocspServices,
                               int remoteSigningServices) {
    }

    public record Provider(String name, String services, String status, String syncedAt, int integrity) {
    }

    public record Activity(String title, String detail, String occurredAt, String level) {
    }

    public record Publication(String version, Instant publishedAt, Instant nextUpdate, String digestAlgorithm,
                              String signatureStatus) {
    }
}
