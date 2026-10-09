package com.electcerti.krdss.tl.model;

import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 불변 TL 데이터. 의미 검증은 별도 수행하며, 파싱 성공은 신뢰 판정이 아니다. */
public record KrTrustList(SchemeInformation schemeInformation, List<TrustServiceProvider> trustServiceProviders) {
    public KrTrustList {
        Objects.requireNonNull(schemeInformation, "schemeInformation");
        trustServiceProviders = List.copyOf(trustServiceProviders);
    }

    public record SchemeInformation(int formatVersion, BigInteger sequenceNumber, String operatorName,
            Instant issueDate, Instant nextUpdate, int historicalInformationPeriod) {
        public SchemeInformation {
            Objects.requireNonNull(sequenceNumber, "sequenceNumber");
            Objects.requireNonNull(operatorName, "operatorName");
            Objects.requireNonNull(issueDate, "issueDate");
            Objects.requireNonNull(nextUpdate, "nextUpdate");
        }

        /** 기존 Java 호출부용: 과거 version 인수는 명시적으로 발행 순번으로 취급한다. */
        @Deprecated
        public SchemeInformation(int legacySequence, String operatorName, Instant issueDate, Instant nextUpdate) {
            this(6, BigInteger.valueOf(legacySequence), operatorName, issueDate, nextUpdate, 65535);
        }

        /** 기존 화면용 순번. 새 호출부는 큰 정수 sequenceNumber를 사용한다. */
        @Deprecated
        public int version() {
            return sequenceNumber.intValueExact();
        }
    }

    public record TrustServiceProvider(String providerId, String name, List<TrustService> services) {
        public TrustServiceProvider {
            Objects.requireNonNull(name, "name");
            services = List.copyOf(services);
        }

        public TrustServiceProvider(String name, List<TrustService> services) {
            this(null, name, services);
        }
    }

    public record LocalizedName(String language, String value) {
        public LocalizedName {
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(value, "value");
        }
    }

    /** 불변 바이너리 값. 내부 DER/SKI를 외부 배열 변경으로부터 보호한다. */
    public record BinaryValue(byte[] bytes) {
        public BinaryValue {
            bytes = Objects.requireNonNull(bytes, "bytes").clone();
        }

        @Override public byte[] bytes() { return bytes.clone(); }
        @Override public boolean equals(Object other) {
            return other instanceof BinaryValue value && Arrays.equals(bytes, value.bytes);
        }
        @Override public int hashCode() { return Arrays.hashCode(bytes); }
    }

    public record CurrentDigitalIdentity(List<BinaryValue> certificates, List<BinaryValue> subjectKeyIdentifiers,
            String subjectName) {
        public CurrentDigitalIdentity {
            certificates = List.copyOf(certificates);
            subjectKeyIdentifiers = List.copyOf(subjectKeyIdentifiers);
        }
    }

    /** 과거 ID에는 인증서 필드를 두지 않는다. 내부 인증서 저장소와 구별한다. */
    public record HistoricalDigitalIdentity(List<BinaryValue> subjectKeyIdentifiers, String subjectName) {
        public HistoricalDigitalIdentity {
            subjectKeyIdentifiers = List.copyOf(subjectKeyIdentifiers);
        }
    }

    /** Extension 내부 XML 요소 목록. 미지원 확장도 namespace·속성·중첩 구조를 보존한다. */
    public record ServiceExtension(boolean critical, List<String> payloadXml) {
        public ServiceExtension {
            payloadXml = List.copyOf(payloadXml);
            if (payloadXml.isEmpty()) throw new IllegalArgumentException("Extension payload is empty");
        }
    }

    public record ServiceInformation(String serviceTypeUri, List<LocalizedName> names,
            CurrentDigitalIdentity digitalIdentity, String statusUri, Instant statusStartingTime,
            List<ServiceExtension> extensions) {
        public ServiceInformation {
            Objects.requireNonNull(serviceTypeUri, "serviceTypeUri");
            names = List.copyOf(names);
            Objects.requireNonNull(digitalIdentity, "digitalIdentity");
            Objects.requireNonNull(statusUri, "statusUri");
            Objects.requireNonNull(statusStartingTime, "statusStartingTime");
            extensions = List.copyOf(extensions);
        }
    }

    public record ServiceHistoryInstance(String serviceTypeUri, List<LocalizedName> names,
            HistoricalDigitalIdentity digitalIdentity, String statusUri, Instant statusStartingTime,
            List<ServiceExtension> extensions) {
        public ServiceHistoryInstance {
            Objects.requireNonNull(serviceTypeUri, "serviceTypeUri");
            names = List.copyOf(names);
            Objects.requireNonNull(digitalIdentity, "digitalIdentity");
            Objects.requireNonNull(statusUri, "statusUri");
            Objects.requireNonNull(statusStartingTime, "statusStartingTime");
            extensions = List.copyOf(extensions);
        }
    }

    public record TrustService(String serviceId, ServiceInformation current, List<ServiceHistoryInstance> history) {
        public TrustService {
            Objects.requireNonNull(current, "current");
            history = List.copyOf(history);
        }

        /** 구형 Java 시연 호출부 전용. 새 관리 입력은 current/history 모델을 사용한다. */
        @Deprecated
        public TrustService(String type, String name, ServiceStatus status, Instant start, byte[] certificate) {
            this(null, new ServiceInformation(type, List.of(new LocalizedName("ko", name)),
                    new CurrentDigitalIdentity(certificate == null ? List.of() : List.of(new BinaryValue(certificate)),
                            List.of(), null),
                    legacyStatusUri(status), start, List.of()), List.of());
        }

        public String serviceTypeIdentifier() { return current.serviceTypeUri(); }
        public String serviceName() { return current.names().isEmpty() ? "" : current.names().get(0).value(); }
        public Instant statusStartingTime() { return current.statusStartingTime(); }
        public byte[] digitalIdentity() {
            return current.digitalIdentity().certificates().isEmpty() ? null
                    : current.digitalIdentity().certificates().get(0).bytes();
        }

        /** 기존 소비자 호환용. 새 PoC 상태는 기존 GRANTED로 자동 승격하지 않는다. */
        public ServiceStatus status() {
            String uri = current.statusUri();
            if (LEGACY_GRANTED.equals(uri)) return ServiceStatus.GRANTED;
            if (LEGACY_WITHDRAWN.equals(uri)) return ServiceStatus.WITHDRAWN;
            if (LEGACY_SUSPENDED.equals(uri)) return ServiceStatus.SUSPENDED;
            return PocTrustListProfile.status(uri);
        }
    }

    public static final String LEGACY_GRANTED = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/granted";
    public static final String LEGACY_WITHDRAWN = "http://uri.etsi.org/TrstSvc/TrustedList/Svcstatus/withdrawn";
    public static final String LEGACY_SUSPENDED = "urn:kr:krdss:TrustedList:Svcstatus:suspended";

    private static String legacyStatusUri(ServiceStatus status) {
        return switch (Objects.requireNonNull(status, "status")) {
            case GRANTED -> LEGACY_GRANTED;
            case WITHDRAWN -> LEGACY_WITHDRAWN;
            case SUSPENDED -> LEGACY_SUSPENDED;
            case ACCREDITED -> PocTrustListProfile.STATUS_ACCREDITED;
            case UNKNOWN -> throw new IllegalArgumentException("Unknown status requires its original URI");
        };
    }

    public enum ServiceStatus { GRANTED, ACCREDITED, WITHDRAWN, SUSPENDED, UNKNOWN }
}
