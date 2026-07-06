package com.electcerti.krdss.dss.pki.ocsp;

import org.bouncycastle.asn1.x509.CRLReason;

import java.time.Instant;
import java.util.Objects;

/**
 * OCSP 응답 1건에 담길 인증서 상태 (RFC 6960 §2.2 CertStatus).
 *
 * <p>{@link OcspStatusSource}가 일련번호(serial)에 대해 반환하는 값으로, {@link OcspResponder}가
 * 이를 BouncyCastle {@code CertificateStatus}로 변환한다.</p>
 *
 * @param kind      상태 종류(good / revoked / unknown)
 * @param revokedAt 폐지·정지 시각({@code REVOKED}일 때만 유효, 그 외 null)
 * @param reason    폐지 사유 코드({@link CRLReason}; 정지는 {@link CRLReason#certificateHold})
 */
public record OcspCertStatus(Kind kind, Instant revokedAt, int reason) {

    /** 상태 종류. */
    public enum Kind {
        /** 유효(정상 발급, 미폐지). */
        GOOD,
        /** 폐지 또는 정지(certificateHold). */
        REVOKED,
        /** 이 CA가 발급 사실을 알지 못하는 일련번호. */
        UNKNOWN
    }

    public OcspCertStatus {
        Objects.requireNonNull(kind, "kind");
        if (kind == Kind.REVOKED) {
            Objects.requireNonNull(revokedAt, "revokedAt (REVOKED 상태 필수)");
        }
    }

    /** 유효 상태. */
    public static OcspCertStatus good() {
        return new OcspCertStatus(Kind.GOOD, null, CRLReason.unspecified);
    }

    /** 폐지 상태(사유 미지정). */
    public static OcspCertStatus revoked(Instant revokedAt) {
        return new OcspCertStatus(Kind.REVOKED, revokedAt, CRLReason.unspecified);
    }

    /** 폐지 상태(사유 지정, {@link CRLReason} 코드). */
    public static OcspCertStatus revoked(Instant revokedAt, int reason) {
        return new OcspCertStatus(Kind.REVOKED, revokedAt, reason);
    }

    /** 정지 상태 — OCSP에서는 {@code certificateHold} 사유의 폐지로 표현한다(재개 가능). */
    public static OcspCertStatus suspended(Instant suspendedAt) {
        return new OcspCertStatus(Kind.REVOKED, suspendedAt, CRLReason.certificateHold);
    }

    /** 미발급/미추적 일련번호. */
    public static OcspCertStatus unknown() {
        return new OcspCertStatus(Kind.UNKNOWN, null, CRLReason.unspecified);
    }
}
