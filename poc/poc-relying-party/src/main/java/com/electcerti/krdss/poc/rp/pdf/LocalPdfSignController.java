package com.electcerti.krdss.poc.rp.pdf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/**
 * 로컬 PDF Baseline 서명·검증 API — {@code /local-sign.html} 화면의 백엔드.
 *
 * <p>Whale PoC(PDF Baseline) 범위: PAdES-BASELINE-B 생성과, 트러스트스토어 + KR-TL 을
 * 입력받는 검증. 원격전자서명·TSA·OCSP 는 사용하지 않는다.</p>
 */
@RestController
@RequestMapping("/api/pdf")
public class LocalPdfSignController {

    private static final Logger log = LoggerFactory.getLogger(LocalPdfSignController.class);

    private final PdfMaterials materials;
    private final PdfBaselineSigner signer;
    private final PdfBaselineVerifier verifier;

    public LocalPdfSignController(PdfMaterials materials, PdfBaselineSigner signer, PdfBaselineVerifier verifier) {
        this.materials = materials;
        this.signer = signer;
        this.verifier = verifier;
    }

    /**
     * 서명된 KR-TL 을 만들어 내려준다.
     *
     * <p>output/certs 의 CA 인증서로 신뢰목록을 구성하고, 신뢰목록 서명자
     * ({@code kisa-tl-signer.p12})로 전자서명한다. 그대로 검증 입력으로 쓴다.</p>
     *
     * @param format {@code xml}(기본, TS 119 612 + XAdES) 또는 {@code jws}(KR-TL JSON + JWS)
     */
    @GetMapping("/krtl/sample")
    public ResponseEntity<byte[]> sampleTrustList(
            @RequestParam(value = "format", defaultValue = "xml") String format) {
        boolean xml = !"jws".equalsIgnoreCase(format);
        byte[] signed = materials.signedSampleTrustList(
                xml ? KrTrustListDocument.Format.XML : KrTrustListDocument.Format.JSON_JWS);
        return ResponseEntity.ok()
                .headers(attachment(xml ? "kr-tl-signed.xml" : "kr-tl-signed.json"))
                .contentType(xml ? MediaType.APPLICATION_XML : MediaType.APPLICATION_JSON)
                .body(signed);
    }

    /**
     * PDF 에 PAdES-BASELINE-B 서명을 생성한다.
     *
     * @param pdf      원본 PDF
     * @param cert     서명용 인증서(PKCS#12)
     * @param password PKCS#12 비밀번호
     */
    @PostMapping(value = "/sign", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> sign(@RequestParam("pdf") MultipartFile pdf,
                                       @RequestParam("cert") MultipartFile cert,
                                       @RequestParam(value = "password", required = false) String password,
                                       @RequestParam(value = "reason", required = false) String reason,
                                       @RequestParam(value = "location", required = false) String location) {
        require(pdf, "서명할 PDF 파일");
        require(cert, "서명용 인증서 파일(PKCS#12)");

        PdfBaselineSigner.SignedPdf signed = signer.sign(
                bytes(pdf), pdf.getOriginalFilename(),
                bytes(cert), password != null ? password.toCharArray() : new char[0],
                reason, location);

        return ResponseEntity.ok()
                .headers(attachment(signed.fileName()))
                .contentType(MediaType.APPLICATION_PDF)
                // 다운로드 응답이라 본문에 실을 수 없는 서명 정보를 헤더로 함께 준다.
                .header("X-Krdss-Signature-Level", signed.signatureLevel())
                .header("X-Krdss-Signature-Algorithm", signed.signatureAlgorithm())
                .header("X-Krdss-Signer", encodeHeader(signed.signerSubject()))
                .body(signed.bytes());
    }

    /**
     * 서명된 PDF 를 검증하고 리포트를 반환한다.
     *
     * @param pdf        서명된 PDF
     * @param truststore 신뢰 앵커(PKCS#12 · JKS · PEM/CRT)
     * @param krtl       KR-TL(XML 또는 JSON) — 선택
     */
    @PostMapping("/verify")
    public PdfVerifyReport verify(@RequestParam("pdf") MultipartFile pdf,
                                  @RequestParam("truststore") MultipartFile truststore,
                                  @RequestParam(value = "truststorePassword", required = false) String truststorePassword,
                                  @RequestParam(value = "krtl", required = false) MultipartFile krtl) {
        require(pdf, "검증할 서명 PDF");
        require(truststore, "트러스트스토어 파일");

        List<X509Certificate> anchors = materials.loadTrustAnchors(
                bytes(truststore),
                truststorePassword != null ? truststorePassword.toCharArray() : null);

        KrTrustListDocument trustList = (krtl != null && !krtl.isEmpty())
                ? materials.loadTrustList(bytes(krtl)) : null;

        return verifier.verify(bytes(pdf), pdf.getOriginalFilename(), anchors,
                truststore.getOriginalFilename(), trustList);
    }

    private static void require(MultipartFile file, String what) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException(what + "을(를) 첨부하세요.");
        }
    }

    private static byte[] bytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static HttpHeaders attachment(String fileName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment().filename(fileName).build());
        return headers;
    }

    /** HTTP 헤더는 ISO-8859-1 만 안전하다. 한글 DN 이 섞여도 깨지지 않게 걸러 낸다. */
    private static String encodeHeader(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[^\\x20-\\x7E]", "?");
    }

    /** 입력 오류는 화면이 그대로 보여 줄 수 있게 400 + 메시지로 돌려준다. */
    @ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
    public ResponseEntity<Map<String, String>> handleBadRequest(RuntimeException e) {
        log.warn("[PDF] 요청 처리 실패: {}", e.getMessage());
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
    }

    /**
     * 필수 첨부가 빠진 경우.
     *
     * <p>기본 처리에 맡기면 화면에 "400 Bad Request" 만 뜨고 무엇이 빠졌는지 알 수 없다.
     * 어떤 파트가 없는지 이름을 담아 돌려준다.</p>
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> handleMissingPart(MissingServletRequestPartException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "필수 첨부가 없습니다: " + e.getRequestPartName()));
    }

    /** 업로드 용량 초과 — application.yml 의 multipart 한도를 안내한다. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "업로드 파일이 너무 큽니다. spring.servlet.multipart 한도를 확인하세요."));
    }
}
