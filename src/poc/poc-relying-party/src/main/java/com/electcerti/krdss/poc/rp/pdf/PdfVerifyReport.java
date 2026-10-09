package com.electcerti.krdss.poc.rp.pdf;

import java.time.Instant;
import java.util.List;

/**
 * PDF 서명 검증 리포트 — 화면에 그대로 뿌리는 응답 모델.
 *
 * <p>최종 판정은 ETSI EN 319 102-1 3분류({@code TOTAL_PASSED} / {@code INDETERMINATE} /
 * {@code TOTAL_FAILED})를 따른다. DSS 의 암호학적·경로 검증 결과에 KR-TL(신뢰목록) 평가를
 * 결합해 산출한다.</p>
 *
 * @param status         종합 판정
 * @param summary        판정 사유 요약(한 줄)
 * @param validationTime 검증 수행 시각
 * @param document       검증 대상 문서 정보
 * @param trustStore     신뢰 앵커 정보
 * @param trustList      KR-TL 평가 결과
 * @param signatures     서명별 상세
 * @param etsiSimpleReportXml ETSI Simple Report(XML) 원문
 */
public record PdfVerifyReport(
        String status,
        String summary,
        Instant validationTime,
        DocumentInfo document,
        TrustStoreInfo trustStore,
        TrustListInfo trustList,
        List<SignatureInfo> signatures,
        String etsiSimpleReportXml) {

    /** 검증 대상 문서. */
    public record DocumentInfo(String fileName, long size) {
    }

    /** 트러스트스토어에서 적재한 신뢰 앵커. */
    public record TrustStoreInfo(String fileName, int certificateCount, List<String> subjects) {
    }

    /**
     * KR-TL 평가 결과.
     *
     * @param provided     KR-TL 이 제출되었는지
     * @param signature    KR-TL 자체의 전자서명 검증 결과
     * @param operator     신뢰목록 운영기관
     * @param version      발행 순번의 십진 문자열(큰 정수의 JSON 정밀도 손실 방지)
     * @param issueDate    발행일
     * @param nextUpdate   다음 갱신 예정일
     * @param serviceCount 수록된 신뢰 서비스 수
     * @param matched      서명자 발급기관과 일치한 신뢰 서비스명(없으면 null)
     * @param serviceStatus 일치한 서비스의 상태(GRANTED 등, 없으면 null)
     * @param detail       평가 사유
     */
    public record TrustListInfo(
            boolean provided,
            TrustListSignature signature,
            String operator,
            String version,
            Instant issueDate,
            Instant nextUpdate,
            int serviceCount,
            String matched,
            String serviceStatus,
            String detail) {
    }

    /**
     * KR-TL 배포본의 전자서명 검증 결과.
     *
     * <p>신뢰목록을 근거로 판정하려면 그 목록 자체가 진본이어야 한다. 서명이 없거나
     * 무효하거나 서명자가 신뢰 앵커로 이어지지 않으면 목록을 판정 근거로 쓰지 않는다.</p>
     *
     * @param format         배포 형식 (XML/XAdES 또는 JSON/JWS)
     * @param signed         전자서명이 붙어 있는지
     * @param signatureValid 서명값이 본문과 일치하는지
     * @param signerTrusted  서명자 인증서가 트러스트스토어 앵커까지 이어지는지
     * @param trustworthy    위 셋을 모두 만족해 판정 근거로 쓸 수 있는지
     * @param algorithm      서명 알고리즘(JWS alg)
     * @param signedBy       서명자 주체 DN
     * @param signerChain    서명자 인증서 체인 DN
     * @param indication     XML(XAdES) 일 때 DSS 판정
     * @param subIndication  XML(XAdES) 일 때 DSS 세부 판정
     * @param detail         검증 사유
     */
    public record TrustListSignature(
            String format,
            boolean signed,
            boolean signatureValid,
            boolean signerTrusted,
            boolean trustworthy,
            String algorithm,
            String signedBy,
            List<String> signerChain,
            String indication,
            String subIndication,
            String detail) {
    }

    /** 서명 한 건의 상세. */
    public record SignatureInfo(
            String id,
            String indication,
            String subIndication,
            String signatureFormat,
            String signedBy,
            String issuer,
            String serialNumber,
            Instant signingTime,
            List<String> certificateChain,
            List<String> errors,
            List<String> warnings,
            List<String> infos) {
    }
}
