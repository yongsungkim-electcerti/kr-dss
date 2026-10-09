package com.electcerti.krdss.tl.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 문서 profile-poc-v1의 임시 URI. 공식 URI 또는 외부 프로파일로 자동 매핑하지 않는다. */
public final class PocTrustListProfile {
    public static final String PREFIX = "urn:example:kr-tl:poc:v1:";
    public static final String EXTENSION_NS = "urn:example:kr-tl:poc:extensions:v1";
    public static final String STATUS_ACCREDITED = PREFIX + "status:accredited";
    public static final String STATUS_SUSPENDED = PREFIX + "status:suspended";
    public static final String STATUS_WITHDRAWN = PREFIX + "status:withdrawn";
    private PocTrustListProfile() { }

    public enum ServiceType { CA, OCSP, TSA }
    public enum TrustDomain { JOINT, SIMPLE }
    public enum TrustPointLevel { ROOT, CA }

    public static String serviceTypeUri(ServiceType type) {
        return PREFIX + "service:" + Objects.requireNonNull(type).name().toLowerCase(java.util.Locale.ROOT);
    }

    public static KrTrustList.ServiceStatus status(String uri) {
        if (STATUS_ACCREDITED.equals(uri)) return KrTrustList.ServiceStatus.ACCREDITED;
        if (STATUS_SUSPENDED.equals(uri)) return KrTrustList.ServiceStatus.SUSPENDED;
        if (STATUS_WITHDRAWN.equals(uri)) return KrTrustList.ServiceStatus.WITHDRAWN;
        return KrTrustList.ServiceStatus.UNKNOWN;
    }

    public static String statusUri(KrTrustList.ServiceStatus status) {
        return switch (Objects.requireNonNull(status)) {
            case ACCREDITED -> STATUS_ACCREDITED;
            case SUSPENDED -> STATUS_SUSPENDED;
            case WITHDRAWN -> STATUS_WITHDRAWN;
            default -> throw new IllegalArgumentException("Not a PoC v1 input status: " + status);
        };
    }

    public static List<KrTrustList.ServiceExtension> extensions(
            ServiceType type, TrustDomain domain, TrustPointLevel level) {
        Objects.requireNonNull(type);
        Objects.requireNonNull(domain);
        if ((type == ServiceType.CA) != (level != null)) {
            throw new IllegalArgumentException("A trust point level is required only for CA");
        }
        var result = new ArrayList<KrTrustList.ServiceExtension>();
        result.add(extension("TrustDomain", domain.name().toLowerCase(java.util.Locale.ROOT)));
        if (level != null) result.add(extension("TrustPointLevel", level == TrustPointLevel.ROOT ? "0" : "1"));
        return List.copyOf(result);
    }

    private static KrTrustList.ServiceExtension extension(String name, String value) {
        return new KrTrustList.ServiceExtension(true,
                List.of("<poc:" + name + " xmlns:poc=\"" + EXTENSION_NS + "\">" + value + "</poc:" + name + ">"));
    }
}
