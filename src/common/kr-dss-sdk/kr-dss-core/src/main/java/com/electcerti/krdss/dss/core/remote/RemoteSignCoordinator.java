package com.electcerti.krdss.dss.core.remote;

import com.electcerti.krdss.ades.core.KrAdesCore;
import com.electcerti.krdss.ades.core.KrAdesCore.DocumentInfo;
import com.electcerti.krdss.dss.api.Dtos.SignRequest;
import com.electcerti.krdss.dss.api.Dtos.SignResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * 원격전자서명 오케스트레이션(생성 흐름 A의 원격 변형).
 *
 * <p>코어가 담당하는 부분은 <b>포맷 독립적인 전·후처리</b>다:</p>
 * <ol>
 *   <li><b>prepare</b> — 문서 다이제스트 계산 + KR-AdES-Core 공통 모델 구성</li>
 *   <li>(원격) {@link RemoteSigner} 콜백 위임 — 다이제스트만 전달, 서명값 수신</li>
 *   <li><b>assemble</b> — 서명값·인증서를 KR-AdES 서명객체로 패키징</li>
 * </ol>
 *
 * <p>원격 전송(CSC/RSSP)·서명활성화(SAD)는 코어가 알지 않으며 SIC 계층이 콜백으로
 * 주입한다. 이로써 "어디서 서명하든" 동일한 KR-AdES 서명객체가 만들어진다.</p>
 */
public class RemoteSignCoordinator {

    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final ObjectMapper mapper = new ObjectMapper();

    /** prepare 결과 — 코어 모델과 서명 대상 다이제스트. */
    public record DataToSign(KrAdesCore core, byte[] digest) {
    }

    /** 콜백 한 번으로 prepare → 원격서명 → assemble 을 수행하는 편의 메서드. */
    public SignResult sign(SignRequest request, RemoteSigner signer) {
        DataToSign dts = prepare(request);
        RemoteSignature signature = signer.signDigest(dts.digest(), DIGEST_ALGORITHM);
        return assemble(dts.core(), signature.signatureValue(), signature.signingCertificate());
    }

    /** 문서 다이제스트를 계산하고 KR-AdES-Core 공통 모델을 구성한다. */
    public DataToSign prepare(SignRequest request) {
        if (request.document() == null || request.document().length == 0) {
            throw new IllegalArgumentException("서명할 문서가 비어 있습니다");
        }
        byte[] digest = digest(request.document());
        DocumentInfo documentInfo = new DocumentInfo(
                "remote-signed." + request.format().documentType().toLowerCase(),
                mimeType(request),
                digest,
                DIGEST_ALGORITHM);
        KrAdesCore core = new KrAdesCore(
                request.format().profileName(),
                request.format(),
                request.level(),
                request.packaging(),
                documentInfo,
                Instant.now(),
                java.util.Map.of("signatureMode", "REMOTE"));
        return new DataToSign(core, digest);
    }

    /**
     * 원격 서명값과 인증서를 KR-AdES 서명객체(데모: KR-JAdES 형식 enveloping)로 패키징한다.
     *
     * <p>스캐폴딩 단계에서는 검증 가능한 JSON 컨테이너로 직렬화한다. 포맷 어댑터
     * (kr-ades-pades/xades 등)가 구현되면 해당 어댑터로 위임하도록 확장한다.</p>
     */
    public SignResult assemble(KrAdesCore core, byte[] signatureValue, byte[] signingCertificate) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("profileId", core.profileId());
            root.put("format", core.format().name());
            root.put("level", core.level().name());
            root.put("packaging", core.packaging().name());
            root.put("signingTime", core.signingTime().toString());
            root.put("signatureMode", "REMOTE");

            ObjectNode doc = root.putObject("documentInfo");
            doc.put("name", core.documentInfo().name());
            doc.put("mimeType", core.documentInfo().mimeType());
            doc.put("digestAlgorithm", core.documentInfo().digestAlgorithm());
            doc.put("digest", Base64.getEncoder().encodeToString(core.documentInfo().digest()));

            root.put("signatureValue", Base64.getEncoder().encodeToString(signatureValue));
            if (signingCertificate != null) {
                root.put("signingCertificate", Base64.getEncoder().encodeToString(signingCertificate));
            }

            byte[] signedDocument = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
            return new SignResult(signedDocument, core.format(), core.level());
        } catch (Exception e) {
            throw new IllegalStateException("KR-AdES 서명객체 패키징 실패", e);
        }
    }

    private byte[] digest(byte[] data) {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM).digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("다이제스트 계산 실패", e);
        }
    }

    private String mimeType(SignRequest request) {
        return switch (request.format()) {
            case PADES -> "application/pdf";
            case XADES -> "application/xml";
            case JADES -> "application/json";
            case CADES -> "application/cms";
            case HADES -> "application/x-hwp";
            case MADES -> "text/markdown";
        };
    }
}
