package com.electcerti.krdss.poc.kisa.registry;

import com.electcerti.krdss.poc.kisa.registry.RegistryDraft.CertificateRecord;
import com.electcerti.krdss.poc.kisa.registry.RegistryDraft.ProviderRecord;
import com.electcerti.krdss.poc.kisa.registry.RegistryDraft.SchemeSettings;
import com.electcerti.krdss.poc.kisa.registry.RegistryDraft.ServiceRecord;
import com.electcerti.krdss.poc.kisa.registry.RegistryException.Code;
import com.electcerti.krdss.tl.builder.KrTrustListXml;
import com.electcerti.krdss.tl.model.KrTrustList;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;
import com.electcerti.krdss.tl.model.PocTrustListProfile;
import com.electcerti.krdss.tl.model.PocTrustListProfile.ServiceType;
import com.electcerti.krdss.tl.model.PocTrustListProfile.TrustDomain;
import com.electcerti.krdss.tl.model.PocTrustListProfile.TrustPointLevel;
import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * 관리자 화면의 사업자·서비스·인증서 편집. 모든 변경은 기대 revision을 받아 새 초안 revision으로 저장한다.
 *
 * <p>정상 편집 규칙만 적용한다. 순번·시각 등 의미 오류 TL은 발행 단계의 시험(TRIAL) 후보에서 만든다.</p>
 */
public class RegistryService {

    static final Duration EXPIRY_WARNING = Duration.ofDays(30);

    private final RegistryStore store;
    private final Clock clock;

    public RegistryService(RegistryStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    // ---------------------------------------------------------------- 입력

    public record ProviderInput(String name, String englishName, String tradeName, String informationUri) {
    }

    /** certificates: 파일 원문(PEM 또는 DER)의 Base64. 한 PEM에 여러 장이 있어도 된다. */
    public record ServiceInput(ServiceType type, String name, String englishName, TrustDomain domain,
            TrustPointLevel level, Instant statusStartingTime, List<String> certificates) {
    }

    /** statusStartingTime: 기준 발행 전에는 정정, 후에는 새 정보 구간의 효력 시각(이후만). null이면 유지한다. */
    public record ServiceInfoInput(String name, String englishName, TrustDomain domain, TrustPointLevel level,
            Instant statusStartingTime) {
    }

    public record StatusChange(ServiceStatus status, Instant effectiveAt, String reason) {
    }

    /** 편집 결과와 인증서 입력 확인 경고. 경고는 저장을 막지 않는다. */
    public record Result(RegistryDraft draft, List<String> warnings) {
        public Result {
            warnings = List.copyOf(warnings);
        }
    }

    // ---------------------------------------------------------------- 조회

    public RegistryStore.Health health() {
        return store.health();
    }

    public RegistryDraft current() {
        return store.current();
    }

    public List<RegistryStore.RevisionSummary> revisions(int limit) {
        return store.revisions(limit);
    }

    /** 저장 상태에서만 계산한 현황. 발행·배포 결과는 포함하지 않는다. */
    public record Overview(long draftRevision, Instant savedAt, String baselineIssuanceId, int providers,
            int services, Map<ServiceStatus, Integer> byStatus, Map<ServiceType, Integer> byType,
            List<ExpiryAlert> expiryAlerts) {
    }

    public record ExpiryAlert(String providerId, String providerName, String serviceId, String serviceName,
            String certificateSha256, Instant notAfter, boolean expired) {
    }

    public Overview overview() {
        var draft = store.current();
        var now = clock.instant();
        var byStatus = new EnumMap<ServiceStatus, Integer>(ServiceStatus.class);
        var byType = new EnumMap<ServiceType, Integer>(ServiceType.class);
        var alerts = new ArrayList<ExpiryAlert>();
        int services = 0;
        for (var provider : draft.providers()) {
            for (var service : provider.services()) {
                services++;
                byStatus.merge(service.status(), 1, Integer::sum);
                byType.merge(service.type(), 1, Integer::sum);
                for (var cert : service.certificates()) {
                    if (cert.notAfter().isBefore(now.plus(EXPIRY_WARNING))) {
                        alerts.add(new ExpiryAlert(provider.providerId(), provider.name(), service.serviceId(),
                                service.name(), cert.sha256(), cert.notAfter(), cert.notAfter().isBefore(now)));
                    }
                }
            }
        }
        alerts.sort((a, b) -> a.notAfter().compareTo(b.notAfter()));
        return new Overview(draft.revision(), draft.savedAt(), draft.baselineIssuanceId(),
                draft.providers().size(), services, byStatus, byType, alerts);
    }

    /** 등록 전 확인용. 저장하지 않는다. */
    public List<CertificateInspector.Inspected> inspect(String base64, ServiceType type, TrustPointLevel level) {
        var now = clock.instant();
        return CertificateInspector.parse(decode(base64)).stream()
                .map(c -> CertificateInspector.inspect(c, type, level, now))
                .toList();
    }

    /**
     * 현재 초안을 서명 전 TL XML로 변환한다. 순번 1·현재 시각은 미리보기 값이며 발행본이 아니다.
     */
    public byte[] previewXml() {
        var issued = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        return KrTrustListXml.toXml(toTrustList(store.current(), BigInteger.ONE, issued, issued.plus(Duration.ofDays(7))));
    }

    /** 초안을 이력 없는 TL 모델로 변환한다(미리보기). 발행 후보의 이력 편성은 발행 기능이 한다. */
    public static KrTrustList toTrustList(RegistryDraft draft, BigInteger sequence, Instant issued, Instant nextUpdate) {
        var providers = new ArrayList<KrTrustList.TrustServiceProvider>();
        for (var provider : draft.providers()) {
            var services = new ArrayList<KrTrustList.TrustService>();
            for (var service : provider.services()) {
                services.add(new KrTrustList.TrustService(service.serviceId(), serviceInformation(service), List.of()));
            }
            providers.add(new KrTrustList.TrustServiceProvider(provider.providerId(), provider.name(), services));
        }
        return new KrTrustList(scheme(draft, sequence, issued, nextUpdate), providers);
    }

    public static KrTrustList.SchemeInformation scheme(RegistryDraft draft, BigInteger sequence, Instant issued,
            Instant nextUpdate) {
        return new KrTrustList.SchemeInformation(6, sequence, draft.scheme().operatorName(), issued, nextUpdate, 65535);
    }

    /** 서비스의 현재 정보(프로파일 v1 URI·확장, 인증서 전체, SKI 중복 제거). */
    public static KrTrustList.ServiceInformation serviceInformation(ServiceRecord service) {
        var names = new ArrayList<KrTrustList.LocalizedName>();
        names.add(new KrTrustList.LocalizedName("ko", service.name()));
        if (service.englishName() != null) {
            names.add(new KrTrustList.LocalizedName("en", service.englishName()));
        }
        var certificates = new ArrayList<KrTrustList.BinaryValue>();
        var skis = new LinkedHashSet<KrTrustList.BinaryValue>();
        for (var cert : service.certificates()) {
            certificates.add(new KrTrustList.BinaryValue(Base64.getDecoder().decode(cert.derBase64())));
            skis.add(new KrTrustList.BinaryValue(HexFormat.of().parseHex(cert.subjectKeyIdentifier())));
        }
        var identity = new KrTrustList.CurrentDigitalIdentity(certificates, List.copyOf(skis),
                service.certificates().get(0).subject());
        return new KrTrustList.ServiceInformation(PocTrustListProfile.serviceTypeUri(service.type()),
                names, identity, PocTrustListProfile.statusUri(service.status()), service.statusStartingTime(),
                PocTrustListProfile.extensions(service.type(), service.domain(), service.level()));
    }

    // ---------------------------------------------------------------- 목록 운영자

    public Result updateScheme(long expectedRevision, SchemeSettings settings) {
        var next = new SchemeSettings(required(settings.operatorName(), "목록 운영자 이름"),
                required(settings.territory(), "국가 코드").toUpperCase(Locale.ROOT));
        if (!next.territory().matches("[A-Z]{2}")) {
            throw invalid("국가 코드는 두 글자(예: KR)여야 합니다.");
        }
        return save(expectedRevision, "목록 운영자 정보 수정", d -> with(d, next, d.providers()));
    }

    // ---------------------------------------------------------------- 사업자

    public Result addProvider(long expectedRevision, ProviderInput input) {
        var name = required(input.name(), "사업자 이름");
        return save(expectedRevision, "사업자 등록: " + name, d -> {
            if (d.providers().stream().anyMatch(p -> p.name().equals(name))) {
                throw invalid("같은 이름의 사업자가 이미 있습니다: " + name);
            }
            var provider = new ProviderRecord(newId("tsp", d), name, optional(input.englishName()),
                    optional(input.tradeName()), uri(input.informationUri()), clock.instant(), List.of());
            var providers = new ArrayList<>(d.providers());
            providers.add(provider);
            return with(d, d.scheme(), providers);
        });
    }

    public Result updateProvider(long expectedRevision, String providerId, ProviderInput input) {
        var name = required(input.name(), "사업자 이름");
        return save(expectedRevision, "사업자 정보 수정: " + name, d -> {
            if (d.providers().stream().anyMatch(p -> p.name().equals(name) && !p.providerId().equals(providerId))) {
                throw invalid("같은 이름의 사업자가 이미 있습니다: " + name);
            }
            return replaceProvider(d, providerId, p -> new ProviderRecord(p.providerId(), name,
                    optional(input.englishName()), optional(input.tradeName()), uri(input.informationUri()),
                    p.registeredAt(), p.services()));
        });
    }

    public Result removeProvider(long expectedRevision, String providerId) {
        var name = provider(store.current(), providerId).name();
        return save(expectedRevision, "사업자 삭제: " + name, d -> {
            requireUnpublished(d);
            provider(d, providerId);
            return with(d, d.scheme(), d.providers().stream().filter(p -> !p.providerId().equals(providerId)).toList());
        });
    }

    // ---------------------------------------------------------------- 서비스

    public Result addService(long expectedRevision, String providerId, ServiceInput input) {
        if (input.type() == null) throw invalid("서비스 유형을 선택하십시오.");
        if (input.domain() == null) throw invalid("분야(공동/간편)를 선택하십시오.");
        checkLevel(input.type(), input.level());
        var name = required(input.name(), "서비스 이름");
        if (input.statusStartingTime() == null) throw invalid("인정 시작 시각을 입력하십시오.");
        var inspected = inspectAll(input.certificates(), input.type(), input.level());
        if (inspected.isEmpty()) throw invalid("서비스 인증서를 하나 이상 입력하십시오.");
        var first = inspected.get(0).certificate();
        for (var item : inspected) {
            if (!sameIdentity(first, item.certificate())) {
                throw new RegistryException(Code.IDENTITY_CONFLICT,
                        "한 서비스의 인증서는 Subject와 공개키가 같아야 합니다. 다른 키는 별도 서비스로 등록하십시오.");
            }
        }
        var records = distinct(inspected);
        return save(expectedRevision, "서비스 등록: " + name, d -> {
            var target = provider(d, providerId);
            checkIdentityUnused(d, null, records);
            var service = new ServiceRecord(newId("svc", d), input.type(), name, optional(input.englishName()),
                    input.domain(), input.level(), ServiceStatus.ACCREDITED, input.statusStartingTime(), null,
                    records);
            var services = new ArrayList<>(target.services());
            services.add(service);
            return replaceProvider(d, providerId, p -> withServices(p, services));
        }, warnings(inspected));
    }

    public Result updateServiceInfo(long expectedRevision, String providerId, String serviceId,
            ServiceInfoInput input) {
        var name = required(input.name(), "서비스 이름");
        if (input.domain() == null) throw invalid("분야(공동/간편)를 선택하십시오.");
        return save(expectedRevision, "서비스 정보 수정: " + name, d -> {
            var service = service(d, providerId, serviceId);
            checkLevel(service.type(), input.level());
            var start = service.statusStartingTime();
            if (input.statusStartingTime() != null && !input.statusStartingTime().equals(start)) {
                // 기준 발행 후에는 정정이 아니라 새 정보 구간의 효력 시각이다. 앞으로만 옮길 수 있다.
                if (d.baselineIssuanceId() != null && !input.statusStartingTime().isAfter(start)) {
                    throw invalid("정상 기준 발행 후에는 효력 시각을 현재 상태 시작 이후로만 지정할 수 있습니다.");
                }
                start = input.statusStartingTime();
            }
            var updated = new ServiceRecord(service.serviceId(), service.type(), name,
                    optional(input.englishName()), input.domain(), input.level(), service.status(), start,
                    service.changeReason(), service.certificates());
            return replaceService(d, providerId, serviceId, updated);
        });
    }

    /** 정상 전이: 인정→정지/철회, 정지→인정(복원)/철회. 효력 시각은 현재 상태 시작 이후여야 한다. */
    public Result changeStatus(long expectedRevision, String providerId, String serviceId, StatusChange change) {
        if (change.status() == null) throw invalid("변경할 상태를 선택하십시오.");
        if (change.effectiveAt() == null) throw invalid("효력 시각을 입력하십시오.");
        var reason = required(change.reason(), "변경 사유");
        var label = service(store.current(), providerId, serviceId).name();
        return save(expectedRevision, "상태 변경: " + label + " → " + statusLabel(change.status()), d -> {
            var service = service(d, providerId, serviceId);
            if (!allowed(service.status(), change.status())) {
                throw invalid(statusLabel(service.status()) + "에서 " + statusLabel(change.status())
                        + "(으)로 변경할 수 없습니다.");
            }
            if (!change.effectiveAt().isAfter(service.statusStartingTime())) {
                throw invalid("효력 시각은 현재 상태 시작(" + service.statusStartingTime() + ") 이후여야 합니다.");
            }
            return replaceService(d, providerId, serviceId, new ServiceRecord(service.serviceId(), service.type(),
                    service.name(), service.englishName(), service.domain(), service.level(), change.status(),
                    change.effectiveAt(), reason, service.certificates()));
        });
    }

    public Result removeService(long expectedRevision, String providerId, String serviceId) {
        var label = service(store.current(), providerId, serviceId).name();
        return save(expectedRevision, "서비스 삭제: " + label, d -> {
            requireUnpublished(d);
            service(d, providerId, serviceId);
            var services = provider(d, providerId).services().stream()
                    .filter(s -> !s.serviceId().equals(serviceId)).toList();
            return replaceProvider(d, providerId, p -> withServices(p, services));
        });
    }

    // ---------------------------------------------------------------- 인증서

    /**
     * 같은 Subject·공개키의 갱신 인증서를 추가한다. 서비스 ID·상태 시작은 유지한다.
     * 새 키·Subject는 거부하며 새 서비스로 등록해야 한다.
     */
    public Result addCertificates(long expectedRevision, String providerId, String serviceId,
            List<String> certificates) {
        var snapshot = service(store.current(), providerId, serviceId);
        var inspected = inspectAll(certificates, snapshot.type(), snapshot.level());
        if (inspected.isEmpty()) throw invalid("추가할 인증서를 입력하십시오.");
        var records = distinct(inspected);
        return save(expectedRevision, "인증서 갱신: " + snapshot.name(), d -> {
            var service = service(d, providerId, serviceId);
            var existing = CertificateInspector.decode(service.certificates().get(0));
            for (var item : inspected) {
                if (!sameIdentity(existing, item.certificate())) {
                    throw new RegistryException(Code.IDENTITY_CONFLICT,
                            "기존 서비스와 Subject 또는 공개키가 다릅니다. 새 키는 새 서비스로 등록하십시오.");
                }
            }
            for (var record : records) {
                if (service.certificates().stream().anyMatch(c -> c.sha256().equals(record.sha256()))) {
                    throw new RegistryException(Code.IDENTITY_CONFLICT, "이미 등록된 인증서입니다: " + record.sha256());
                }
            }
            checkIdentityUnused(d, serviceId, records);
            var merged = new ArrayList<>(service.certificates());
            merged.addAll(records);
            return replaceService(d, providerId, serviceId, withCertificates(service, merged));
        }, warnings(inspected));
    }

    /** 정상 기준 발행 전 잘못 넣은 인증서 정정용. 마지막 한 장은 지울 수 없다. */
    public Result removeCertificate(long expectedRevision, String providerId, String serviceId, String sha256) {
        var label = service(store.current(), providerId, serviceId).name();
        return save(expectedRevision, "인증서 삭제: " + label, d -> {
            requireUnpublished(d);
            var service = service(d, providerId, serviceId);
            var remaining = service.certificates().stream().filter(c -> !c.sha256().equals(sha256)).toList();
            if (remaining.size() == service.certificates().size()) {
                throw new RegistryException(Code.NOT_FOUND, "인증서를 찾을 수 없습니다: " + sha256);
            }
            if (remaining.isEmpty()) throw invalid("서비스에는 인증서가 하나 이상 있어야 합니다.");
            return replaceService(d, providerId, serviceId, withCertificates(service, remaining));
        });
    }

    // ---------------------------------------------------------------- 내부

    private Result save(long expectedRevision, String change, UnaryOperator<RegistryDraft> edit) {
        return save(expectedRevision, change, edit, List.of());
    }

    private Result save(long expectedRevision, String change, UnaryOperator<RegistryDraft> edit,
            List<String> warnings) {
        return new Result(store.update(expectedRevision, change, edit), warnings);
    }

    private List<CertificateInspector.Inspected> inspectAll(List<String> inputs, ServiceType type,
            TrustPointLevel level) {
        var result = new ArrayList<CertificateInspector.Inspected>();
        if (inputs == null) return result;
        for (var input : inputs) result.addAll(inspect(input, type, level));
        return result;
    }

    private static List<CertificateRecord> distinct(List<CertificateInspector.Inspected> inspected) {
        var seen = new HashSet<String>();
        var records = new ArrayList<CertificateRecord>();
        for (var item : inspected) {
            if (seen.add(item.record().sha256())) records.add(item.record());
        }
        return records;
    }

    private static List<String> warnings(List<CertificateInspector.Inspected> inspected) {
        var warnings = new ArrayList<String>();
        for (var item : inspected) {
            for (var warning : item.warnings()) {
                warnings.add(item.record().subject() + ": " + warning);
            }
        }
        return warnings;
    }

    /** 다른 서비스가 이미 쓰는 인증서·공개키는 등록하지 않는다. */
    private static void checkIdentityUnused(RegistryDraft draft, String exceptServiceId,
            List<CertificateRecord> records) {
        for (var provider : draft.providers()) {
            for (var service : provider.services()) {
                if (service.serviceId().equals(exceptServiceId)) continue;
                for (var cert : service.certificates()) {
                    for (var record : records) {
                        if (cert.sha256().equals(record.sha256())
                                || cert.publicKeySha256().equals(record.publicKeySha256())) {
                            throw new RegistryException(Code.IDENTITY_CONFLICT, "같은 공개키가 이미 "
                                    + provider.name() + " / " + service.name() + " 서비스에 등록되어 있습니다.");
                        }
                    }
                }
            }
        }
    }

    private static boolean sameIdentity(X509Certificate a, X509Certificate b) {
        return a.getSubjectX500Principal().equals(b.getSubjectX500Principal())
                && Arrays.equals(a.getPublicKey().getEncoded(), b.getPublicKey().getEncoded());
    }

    private static boolean allowed(ServiceStatus from, ServiceStatus to) {
        return switch (from) {
            case ACCREDITED -> to == ServiceStatus.SUSPENDED || to == ServiceStatus.WITHDRAWN;
            case SUSPENDED -> to == ServiceStatus.ACCREDITED || to == ServiceStatus.WITHDRAWN;
            default -> false;
        };
    }

    static String statusLabel(ServiceStatus status) {
        return switch (status) {
            case ACCREDITED -> "인정";
            case SUSPENDED -> "정지";
            case WITHDRAWN -> "철회";
            default -> status.name();
        };
    }

    private static void checkLevel(ServiceType type, TrustPointLevel level) {
        if (type == ServiceType.CA && level == null) {
            throw invalid("CA 서비스는 신뢰점 레벨(RootCA 0 / CA 1)을 선택해야 합니다.");
        }
        if (type != ServiceType.CA && level != null) {
            throw invalid("신뢰점 레벨은 CA 서비스에만 지정합니다.");
        }
    }

    /** 정상 기준 발행 이후 등재 항목은 삭제 대신 철회해야 한다. */
    private static void requireUnpublished(RegistryDraft draft) {
        if (draft.baselineIssuanceId() != null) {
            throw invalid("정상 기준 발행 이후에는 삭제·시작 시각 정정 대신 상태 변경(철회)을 사용하십시오.");
        }
    }

    private static RegistryDraft with(RegistryDraft d, SchemeSettings scheme, List<ProviderRecord> providers) {
        return new RegistryDraft(d.schemaVersion(), d.revision(), d.parentRevision(), d.savedAt(), d.change(),
                d.baselineIssuanceId(), scheme, providers);
    }

    private static RegistryDraft replaceProvider(RegistryDraft d, String providerId,
            UnaryOperator<ProviderRecord> change) {
        provider(d, providerId);
        var providers = d.providers().stream()
                .map(p -> p.providerId().equals(providerId) ? change.apply(p) : p)
                .toList();
        return with(d, d.scheme(), providers);
    }

    private static RegistryDraft replaceService(RegistryDraft d, String providerId, String serviceId,
            ServiceRecord updated) {
        return replaceProvider(d, providerId, p -> withServices(p, p.services().stream()
                .map(s -> s.serviceId().equals(serviceId) ? updated : s)
                .toList()));
    }

    private static ProviderRecord withServices(ProviderRecord p, List<ServiceRecord> services) {
        return new ProviderRecord(p.providerId(), p.name(), p.englishName(), p.tradeName(), p.informationUri(),
                p.registeredAt(), services);
    }

    private static ServiceRecord withCertificates(ServiceRecord s, List<CertificateRecord> certificates) {
        return new ServiceRecord(s.serviceId(), s.type(), s.name(), s.englishName(), s.domain(), s.level(),
                s.status(), s.statusStartingTime(), s.changeReason(), certificates);
    }

    static ProviderRecord provider(RegistryDraft d, String providerId) {
        return d.providers().stream().filter(p -> p.providerId().equals(providerId)).findFirst()
                .orElseThrow(() -> new RegistryException(Code.NOT_FOUND, "사업자를 찾을 수 없습니다: " + providerId));
    }

    static ServiceRecord service(RegistryDraft d, String providerId, String serviceId) {
        return provider(d, providerId).services().stream().filter(s -> s.serviceId().equals(serviceId))
                .findFirst()
                .orElseThrow(() -> new RegistryException(Code.NOT_FOUND, "서비스를 찾을 수 없습니다: " + serviceId));
    }

    private static String newId(String prefix, RegistryDraft d) {
        Set<String> used = new HashSet<>();
        for (var p : d.providers()) {
            used.add(p.providerId());
            for (var s : p.services()) used.add(s.serviceId());
        }
        String id;
        do {
            id = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        } while (used.contains(id));
        return id;
    }

    private static byte[] decode(String base64) {
        if (base64 == null || base64.isBlank()) throw invalid("인증서 입력이 비어 있습니다.");
        try {
            return Base64.getMimeDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw invalid("인증서 입력의 Base64 형식이 올바르지 않습니다.");
        }
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw invalid(label + "을(를) 입력하십시오.");
        return value.strip();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String uri(String value) {
        var text = optional(value);
        if (text == null) return null;
        try {
            var uri = new URI(text);
            if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()) || uri.getHost() == null) {
                throw invalid("정보 URI는 http(s) 주소여야 합니다.");
            }
            return text;
        } catch (URISyntaxException e) {
            throw invalid("정보 URI 형식이 올바르지 않습니다.");
        }
    }

    private static RegistryException invalid(String message) {
        return new RegistryException(Code.INVALID_INPUT, message);
    }
}
