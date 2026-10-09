package com.electcerti.krdss.poc.kisa.issuance;

import java.time.Instant;
import java.util.List;

/** 발행 요청·진단·발행본 메타데이터. */
public final class Issuance {
    private Issuance() {
    }

    /** BASELINE은 정상 기준 이력·순번을 전진시키고, TRIAL은 그대로 둔다. XML에 싣지 않는 관리 값이다. */
    public enum Purpose { BASELINE, TRIAL }

    /** 관리자가 명시한 발행 입력. 비어 있는 값은 최초 처리 때 자동값으로 고정한다. */
    public record Request(String requestId, Long expectedDraftRevision, Purpose purpose, String sequenceNumber,
            Instant issuedAt, Instant nextUpdate) {
    }

    /** 정상 규칙 사전 검사 결과. 경고는 생성을 막지 않지만 BASELINE을 TRIAL로 바꾼다. */
    public record Diagnostic(String code, String field, String message) {
    }

    /** 발행 전 계획: 권장값과 입력값에 대한 진단. 아무것도 저장하지 않는다. */
    public record Plan(long draftRevision, String baselineIssuanceId, String normalSequence,
            String recommendedSequence, String sequenceNumber, Instant issuedAt, Instant nextUpdate,
            Purpose requestedPurpose, Purpose purpose, List<Diagnostic> diagnostics, boolean signerReady,
            String signerDetail) {
    }

    /** issued/&lt;issuanceId&gt;/manifest.json. 개인키는 넣지 않는다. */
    public record Manifest(int manifestVersion, String issuanceId, String requestId, String inputDigest,
            Purpose requestedPurpose, Purpose purpose, String listId, String sequenceNumber, Instant issuedAt,
            Instant nextUpdate, long draftRevision, String previousBaselineIssuanceId, String xmlSha256,
            long xmlSize, String profileSha256, String signerCertificateSha256, String signerSubject,
            boolean signatureValid, String signatureDetail, List<Diagnostic> diagnostics, Instant signedAt) {
    }

    /** requests/&lt;requestId&gt;.json. 진행·실패 진단용이며 성공의 권위는 state.json이다. */
    public record RequestRecord(String requestId, String status, String inputDigest, Request request,
            String sequenceNumber, Instant issuedAt, Instant nextUpdate, Purpose purpose, Long draftRevision,
            String issuanceId, String errorCode, String errorMessage, Instant updatedAt) {
    }

    /** 발행 결과. replay=true면 같은 요청의 기존 결과를 다시 돌려준 것이다(재서명 없음). */
    public record Result(Manifest manifest, boolean replay) {
    }

    /** 발행본 목록 한 줄. */
    public record Entry(Manifest manifest, String state, boolean baseline, boolean intact, String integrityDetail) {
    }
}
