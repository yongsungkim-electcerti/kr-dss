package com.electcerti.krdss.poc.kisa.web;

import com.electcerti.krdss.poc.kisa.issuance.IssuanceService;
import com.electcerti.krdss.poc.kisa.registry.RegistryException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * IF-07 신뢰목록 공개 조회(설계 28). 인증 없음, 조회 전용.
 *
 * <ul>
 *   <li>{@code GET /tl/kr-tl.xml} — 선택된 발행본의 서명 XML 원문. ETag = 바이트 SHA-256, If-None-Match → 304</li>
 *   <li>{@code GET /tl/kr-tl.sha2} — 같은 바이트의 SHA-256 raw 32바이트</li>
 * </ul>
 *
 * <p>한 요청 안에서 상태 스냅숏 하나로 제공 ID를 고정한다. 재서명·재직렬화하지 않는다. 의미 오류 TL도 200으로
 * 제공하며 수용 판단은 소비자 몫이다. 손상·상태 불가는 503이며 다른 발행본으로 대체하지 않는다.</p>
 */
@RestController
@RequestMapping("/tl")
@CrossOrigin(origins = "*", allowCredentials = "false", exposedHeaders = HttpHeaders.ETAG,
        allowedHeaders = HttpHeaders.IF_NONE_MATCH, methods = {RequestMethod.GET, RequestMethod.HEAD})
public class TlPublicController {

    static final MediaType TSL_XML = MediaType.parseMediaType("application/vnd.etsi.tsl+xml");
    private static final CacheControl NO_CACHE = CacheControl.noCache().noTransform();
    private static final String ALLOW = "GET, HEAD, OPTIONS";

    private final IssuanceService issuance;

    public TlPublicController(IssuanceService issuance) {
        this.issuance = issuance;
    }

    @RequestMapping(value = "/kr-tl.xml", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<byte[]> xml(@RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        if (!acceptable(accept, TSL_XML)) return error(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE");
        var served = served();
        if (served.error() != null) return served.error();
        String etag = "\"sha256-" + served.value().sha256() + "\"";
        if (matches(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(NO_CACHE).build();
        }
        return ResponseEntity.ok().contentType(TSL_XML).eTag(etag).cacheControl(NO_CACHE).body(served.value().xml());
    }

    @RequestMapping(value = "/kr-tl.sha2", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<byte[]> sha2(@RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept) {
        if (!acceptable(accept, MediaType.APPLICATION_OCTET_STREAM)) return error(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE");
        var served = served();
        if (served.error() != null) return served.error();
        byte[] raw = HexFormat.of().parseHex(served.value().sha256());
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).contentLength(raw.length)
                .cacheControl(NO_CACHE).body(raw);
    }

    /** 조회 경로의 변경 메서드는 지원하지 않는다. */
    @RequestMapping(value = {"/kr-tl.xml", "/kr-tl.sha2"},
            method = {RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE})
    public ResponseEntity<byte[]> notAllowed() {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).header(HttpHeaders.ALLOW, ALLOW)
                .cacheControl(CacheControl.noStore()).contentType(TEXT)
                .body("METHOD_NOT_ALLOWED".getBytes(StandardCharsets.UTF_8));
    }

    // ---------------------------------------------------------------- 내부

    private static final MediaType TEXT = new MediaType("text", "plain", StandardCharsets.UTF_8);

    private record Outcome(IssuanceService.Served value, ResponseEntity<byte[]> error) {
    }

    private Outcome served() {
        try {
            var served = issuance.served();
            if (served == null) return new Outcome(null, error(HttpStatus.NOT_FOUND, "TL_NOT_PUBLISHED"));
            return new Outcome(served, null);
        } catch (RegistryException e) {
            return new Outcome(null, error(HttpStatus.SERVICE_UNAVAILABLE, "TL_UNAVAILABLE"));
        } catch (RuntimeException e) {
            return new Outcome(null, error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR"));
        }
    }

    private static ResponseEntity<byte[]> error(HttpStatus status, String code) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).contentType(TEXT)
                .body(code.getBytes(StandardCharsets.UTF_8));
    }

    /** Accept 생략·와일드카드는 수용한다. 명시 목록에 응답 매체 유형이 없으면 406. */
    static boolean acceptable(String accept, MediaType produced) {
        if (accept == null || accept.isBlank()) return true;
        try {
            List<MediaType> types = MediaType.parseMediaTypes(accept);
            return types.stream().anyMatch(t -> t.getQualityValue() > 0 && t.includes(produced));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }

    /** If-None-Match 약한 비교(RFC 9110 §13.1.2). */
    static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) return false;
        for (String candidate : ifNoneMatch.split(",")) {
            String tag = candidate.strip();
            if (tag.equals("*")) return true;
            if (tag.startsWith("W/")) tag = tag.substring(2);
            if (tag.equals(etag)) return true;
        }
        return false;
    }
}
