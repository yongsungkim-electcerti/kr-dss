package com.electcerti.krdss.tl.builder;

import com.electcerti.krdss.tl.model.KrTrustList;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;

/** 구형 JSON의 version을 순번으로 해석하는 명시적 호환 변환. XML 파서와 공유하지 않는다. */
final class LegacyKrTrustListJson {
    private LegacyKrTrustListJson() { }

    static KrTrustList read(JsonNode root) throws Exception {
        JsonNode scheme = root.required("schemeInformation");
        if (!scheme.has("version")) {
            return KrTrustListBuilder.mapper().treeToValue(root, KrTrustList.class);
        }
        if (scheme.has("formatVersion") || scheme.has("sequenceNumber")) {
            throw new IllegalArgumentException("Mixed legacy and current scheme fields");
        }
        if (!scheme.required("version").isIntegralNumber()) throw new IllegalArgumentException("Invalid legacy version");
        var information = new KrTrustList.SchemeInformation(6, scheme.get("version").bigIntegerValue(),
                text(scheme, "operatorName"), Instant.parse(text(scheme, "issueDate")),
                Instant.parse(text(scheme, "nextUpdate")), 65535);
        var providers = new ArrayList<KrTrustList.TrustServiceProvider>();
        JsonNode providerNodes = root.required("trustServiceProviders");
        if (!providerNodes.isArray()) throw new IllegalArgumentException("Expected providers array");
        for (JsonNode p : providerNodes) {
            var services = new ArrayList<KrTrustList.TrustService>();
            JsonNode serviceNodes = p.required("services");
            if (!serviceNodes.isArray()) throw new IllegalArgumentException("Expected services array");
            for (JsonNode s : serviceNodes) {
                services.add(new KrTrustList.TrustService(text(s, "serviceTypeIdentifier"), text(s, "serviceName"),
                        KrTrustList.ServiceStatus.valueOf(text(s, "status")),
                        Instant.parse(text(s, "statusStartingTime")),
                        s.hasNonNull("digitalIdentity") ? Base64.getDecoder().decode(text(s, "digitalIdentity")) : null));
            }
            providers.add(new KrTrustList.TrustServiceProvider(text(p, "name"), services));
        }
        return new KrTrustList(information, providers);
    }

    private static String text(JsonNode parent, String field) {
        JsonNode value = parent.required(field);
        if (!value.isTextual()) throw new IllegalArgumentException("Expected text: " + field);
        return value.textValue();
    }
}
