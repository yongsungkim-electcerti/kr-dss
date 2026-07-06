package com.electcerti.krdss.dss.trust;

import com.electcerti.krdss.tl.model.KrTrustList;
import com.electcerti.krdss.tl.model.KrTrustList.ServiceStatus;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Layer 1 — 신뢰서비스 목록 (특허-C 청구항 1·5·10).
 *
 * <p>PKI 신뢰서비스 제공자(CA/TSA/OCSP)를 등록·관리한다. 구조는 ETSI TS 119 612 / EU-TL 을
 * 준용하며(청구항 10), {@link KrTrustList}(kr-tl-model) 를 입수해 서비스 식별자 단위로 상태를
 * 조회·갱신할 수 있게 한다.</p>
 */
public final class TrustServiceRegistry {

    /** 등록된 신뢰서비스 항목. */
    public record TrustServiceEntry(String serviceId, String serviceType,
                                    ServiceStatus status, String provider) {
        public TrustServiceEntry withStatus(ServiceStatus newStatus) {
            return new TrustServiceEntry(serviceId, serviceType, newStatus, provider);
        }
    }

    private final ConcurrentHashMap<String, TrustServiceEntry> services = new ConcurrentHashMap<>();

    /** KR-TL(Layer1 모델)을 입수해 신뢰서비스 항목을 채운다(서비스명 기준 색인). */
    public TrustServiceRegistry ingest(KrTrustList trustList) {
        for (KrTrustList.TrustServiceProvider tsp : trustList.trustServiceProviders()) {
            for (KrTrustList.TrustService svc : tsp.services()) {
                register(new TrustServiceEntry(
                        svc.serviceName(), svc.serviceTypeIdentifier(), svc.status(), tsp.name()));
            }
        }
        return this;
    }

    public TrustServiceRegistry register(TrustServiceEntry entry) {
        services.put(entry.serviceId(), entry);
        return this;
    }

    public Optional<TrustServiceEntry> find(String serviceId) {
        return serviceId == null ? Optional.empty() : Optional.ofNullable(services.get(serviceId));
    }

    /** 서비스 상태 변경(청구항 8 — 서비스 제공자 폐지 등). */
    public void setStatus(String serviceId, ServiceStatus status) {
        services.computeIfPresent(serviceId, (k, e) -> e.withStatus(status));
    }

    public int size() {
        return services.size();
    }
}
