package com.electcerti.krdss.tl.model;

import com.electcerti.krdss.tl.model.KrTrustList.*;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** 정상 편집용 변경 연산. 의미 오류 시험은 별도 후보 레코드를 구성하여 원본을 보존한다. */
public final class TrustServiceChanges {
    private TrustServiceChanges() { }

    /** 같은 공개키·Subject 인증서를 중복 없이 추가한다. 상태 시작과 이력은 유지한다. */
    public static TrustService renewCertificates(TrustService service, List<BinaryValue> additions) {
        var current = service.current();
        var identity = current.digitalIdentity();
        var certificates = new ArrayList<>(identity.certificates());
        if (certificates.isEmpty()) throw new IllegalArgumentException("No existing certificate");
        var original = certificate(certificates.get(0));
        for (var existing : certificates) sameIdentity(original, certificate(existing));
        for (var addition : additions) {
            sameIdentity(original, certificate(addition));
            if (!certificates.contains(addition)) certificates.add(addition);
        }
        var updated = new CurrentDigitalIdentity(certificates, identity.subjectKeyIdentifiers(), identity.subjectName());
        return new TrustService(service.serviceId(), new ServiceInformation(current.serviceTypeUri(), current.names(),
                updated, current.statusUri(), current.statusStartingTime(), current.extensions()), service.history());
    }

    /** 새 정보 구간을 추가한다. 새 키·Subject는 새 serviceId로 등록해야 한다. */
    public static TrustService changeInformation(TrustService service, ServiceInformation next, Instant firstIssuedAt) {
        Objects.requireNonNull(firstIssuedAt);
        var current = service.current();
        if (current.equals(next)) return service;
        if (!next.statusStartingTime().isAfter(current.statusStartingTime())
                || next.statusStartingTime().isBefore(firstIssuedAt)) {
            throw new IllegalArgumentException("Invalid or retroactive status start");
        }
        if (current.digitalIdentity().certificates().isEmpty() || next.digitalIdentity().certificates().isEmpty()) {
            throw new IllegalArgumentException("Missing certificate");
        }
        var original = certificate(current.digitalIdentity().certificates().get(0));
        for (var cert : current.digitalIdentity().certificates()) sameIdentity(original, certificate(cert));
        for (var cert : next.digitalIdentity().certificates()) sameIdentity(original, certificate(cert));
        // 상태/이름을 바꿀 때도 과거 검증용 인증서를 현재 집합에서 제거하지 않는다.
        var certificates = new ArrayList<>(current.digitalIdentity().certificates());
        for (var cert : next.digitalIdentity().certificates()) {
            if (!certificates.contains(cert)) certificates.add(cert);
        }
        var identifiers = new ArrayList<>(current.digitalIdentity().subjectKeyIdentifiers());
        for (var ski : next.digitalIdentity().subjectKeyIdentifiers()) {
            if (!identifiers.contains(ski)) identifiers.add(ski);
        }
        next = new ServiceInformation(next.serviceTypeUri(), next.names(),
                new CurrentDigitalIdentity(certificates, identifiers, next.digitalIdentity().subjectName()),
                next.statusUri(), next.statusStartingTime(), next.extensions());
        var skis = current.digitalIdentity().subjectKeyIdentifiers();
        if (skis.isEmpty()) throw new IllegalArgumentException("Historical X509SKI must be supplied");
        if (TrustServiceTimeline.resolve(service, current.statusStartingTime()).reason()
                == TrustServiceTimeline.Reason.INVALID_HISTORY) {
            throw new IllegalArgumentException("Existing history is invalid");
        }
        var history = new ArrayList<ServiceHistoryInstance>();
        history.add(new ServiceHistoryInstance(current.serviceTypeUri(), current.names(),
                new HistoricalDigitalIdentity(skis, current.digitalIdentity().subjectName()),
                current.statusUri(), current.statusStartingTime(), current.extensions()));
        history.addAll(service.history());
        return new TrustService(service.serviceId(), next, history);
    }

    private static X509Certificate certificate(BinaryValue value) {
        try {
            var stream = new ByteArrayInputStream(value.bytes());
            var certificate = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(stream);
            if (stream.available() != 0) throw new IllegalArgumentException("Trailing certificate bytes");
            return certificate;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid X.509 certificate", e);
        }
    }

    private static void sameIdentity(X509Certificate expected, X509Certificate candidate) {
        if (!expected.getSubjectX500Principal().equals(candidate.getSubjectX500Principal())
                || !Arrays.equals(expected.getPublicKey().getEncoded(), candidate.getPublicKey().getEncoded())) {
            throw new IllegalArgumentException("A new key or Subject requires a separate service");
        }
    }
}
