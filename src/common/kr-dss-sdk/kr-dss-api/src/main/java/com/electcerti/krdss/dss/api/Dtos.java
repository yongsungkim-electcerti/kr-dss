package com.electcerti.krdss.dss.api;

import com.electcerti.krdss.ades.core.KrAdesFormat;
import com.electcerti.krdss.ades.core.KrAdesLevel;
import com.electcerti.krdss.ades.core.PackagingType;

/**
 * SDK 공통 요청/결과 DTO 모음.
 *
 * <p>스캐폴딩 단계의 최소 형태이며, 세부 필드는 설계 단계에서 확장한다.</p>
 */
public final class Dtos {

    private Dtos() {
    }

    // --- 요청 ---
    public record SignRequest(byte[] document, KrAdesFormat format, KrAdesLevel level, PackagingType packaging) {
    }

    public record ExtractRequest(byte[] signedDocument, KrAdesFormat format) {
    }

    public record VerifyRequest(byte[] signedDocument, KrAdesFormat format) {
    }

    public record TimestampRequest(byte[] data, String tsaUrl) {
    }

    // --- 결과 ---
    public record SignResult(byte[] signedDocument, KrAdesFormat format, KrAdesLevel level) {
    }

    public record ExtractResult(byte[] signature, byte[] manifest, KrAdesFormat format) {
    }

    public record VerifyResult(VerificationStatus status, byte[] report) {
    }

    public record TimestampResult(byte[] timestampToken) {
    }
}
