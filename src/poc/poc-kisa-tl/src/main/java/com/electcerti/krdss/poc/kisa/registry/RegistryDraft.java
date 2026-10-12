package com.electcerti.krdss.poc.kisa.registry;

import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;
import com.electcerti.krdss.tl.model.PocTrustListProfile.ServiceType;
import com.electcerti.krdss.tl.model.PocTrustListProfile.TrustDomain;
import com.electcerti.krdss.tl.model.PocTrustListProfile.TrustPointLevel;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 불변 초안 revision. 저장할 때마다 새 revision 파일이 되며 기존 파일은 고치지 않는다.
 *
 * <p>정상 기준(BASELINE) 발행 전에는 편집 내용이 현재 정보만 바꾼다. 서비스 이력은
 * 발행 후보에서 기준 대비 최종 변경만 반영하는 발행 단계의 책임이다.</p>
 */
public record RegistryDraft(int schemaVersion, long revision, Long parentRevision, Instant savedAt,
        String change, String baselineIssuanceId, SchemeSettings scheme, List<ProviderRecord> providers) {

    public static final int SCHEMA_VERSION = 1;

    public RegistryDraft {
        Objects.requireNonNull(savedAt, "savedAt");
        Objects.requireNonNull(scheme, "scheme");
        providers = List.copyOf(providers);
    }

    public static RegistryDraft initial(Instant now) {
        return new RegistryDraft(SCHEMA_VERSION, 1, null, now, "초기 초안 생성", null,
                new SchemeSettings("한국인터넷진흥원", "KR"), List.of());
    }

    /** 목록 운영자 정보. 순번·발행 시각은 발행 요청에서 정한다. */
    public record SchemeSettings(String operatorName, String territory) {
        public SchemeSettings {
            Objects.requireNonNull(operatorName, "operatorName");
            Objects.requireNonNull(territory, "territory");
        }
    }

    /** providerId는 내부 식별자로 이름이 바뀌어도 유지한다. */
    public record ProviderRecord(String providerId, String name, String englishName, String tradeName,
            String informationUri, Instant registeredAt, List<ServiceRecord> services) {
        public ProviderRecord {
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(name, "name");
            services = List.copyOf(services);
        }
    }

    /**
     * 하나의 서비스 디지털 ID(같은 Subject·공개키). 키가 바뀌면 새 serviceId로 등록한다.
     * trustPointLevel은 CA에만 둔다.
     */
    public record ServiceRecord(String serviceId, ServiceType type, String name, String englishName,
            TrustDomain domain, TrustPointLevel level, ServiceStatus status, Instant statusStartingTime,
            String changeReason, List<CertificateRecord> certificates) {
        public ServiceRecord {
            Objects.requireNonNull(serviceId, "serviceId");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(domain, "domain");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(statusStartingTime, "statusStartingTime");
            certificates = List.copyOf(certificates);
        }
    }

    /** 인증서 원문(DER)과 화면 표시·검사용으로 추출한 값. 원문이 권위다. */
    public record CertificateRecord(String derBase64, String sha256, String subject, String issuer,
            String serialNumber, Instant notBefore, Instant notAfter, String publicKeyAlgorithm,
            String publicKeySha256, String subjectKeyIdentifier, boolean skiFromExtension, Instant addedAt) {
        public CertificateRecord {
            Objects.requireNonNull(derBase64, "derBase64");
            Objects.requireNonNull(sha256, "sha256");
            Objects.requireNonNull(subjectKeyIdentifier, "subjectKeyIdentifier");
        }
    }
}
