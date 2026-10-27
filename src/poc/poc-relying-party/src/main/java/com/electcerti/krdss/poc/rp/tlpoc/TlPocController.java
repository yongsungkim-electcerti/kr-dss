package com.electcerti.krdss.poc.rp.tlpoc;

import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.ResponseEntity;

/** 서버에서 명시적으로 활성화하는 로컬 실증 API. */
@RestController
@RequestMapping("/api/tl-poc")
@ConditionalOnProperty(name = "krtl.poc.enabled", havingValue = "true")
public class TlPocController {
    private final TlAcceptance acceptance;
    private final Path root;
    private final PocRunStore runs;
    public TlPocController(@Value("${krtl.poc.storage-root}") String storage,
            @Value("${krtl.poc.anchors}") String anchors) throws Exception {
        root = Path.of(storage).toAbsolutePath().normalize();
        acceptance = new TlAcceptance(root.resolve("tl"),
                TlAcceptance.certificates(Files.readAllBytes(Path.of(anchors))), Clock.systemUTC());
        runs = new PocRunStore(root.resolve("runs"));
    }
    @GetMapping("/active") public Map<String, Object> active() throws Exception {
        var result = new LinkedHashMap<String, Object>(); result.put("active", acceptance.view()); result.put("document", acceptance.document()); return result;
    }
    @PostMapping("/inspect") public TlAcceptance.Review inspect(@RequestParam("tl") MultipartFile file) throws Exception {
        if (file.getSize() > TlAcceptance.MAX_TL) throw new TlAcceptance.Rejected("TL_SIZE_LIMIT");
        return acceptance.review(file.getBytes());
    }
    @PostMapping("/adopt") public TlAcceptance.View adopt(@RequestParam String candidateId) throws Exception {
        return acceptance.apply(candidateId);
    }
    @GetMapping("/xml/{id}") public ResponseEntity<byte[]> xml(@PathVariable String id) {
        return download(acceptance.xml(id));
    }
    @GetMapping("/runs/{id}/tl") public TlReview.Document runTl(@PathVariable String id) throws Exception {
        var xml = runs.tl(id); return xml == null ? null : TlReview.describe(xml);
    }
    @GetMapping("/runs/{id}/xml") public ResponseEntity<byte[]> runXml(@PathVariable String id) throws Exception {
        var xml = runs.tl(id); if (xml == null) throw new TlAcceptance.Rejected("NO_VALID_TL"); return download(xml);
    }
    @GetMapping("/tl-diff") public List<TlReview.Change> tlDiff(@RequestParam String baseline, @RequestParam String comparison) throws Exception {
        return TlReview.compare(runTl(baseline), runTl(comparison));
    }
    private ResponseEntity<byte[]> download(byte[] xml) {
        return ResponseEntity.ok().header("Content-Type", "application/octet-stream")
                .header("Content-Disposition", "attachment; filename=trust-list.xml")
                .header("X-Content-Type-Options", "nosniff").body(xml);
    }
    @PostMapping("/verify") public PocRunStore.Run verify(@RequestParam("pdf") MultipartFile pdf,
            @RequestParam("at") Instant at, @RequestParam(value = "certificates", required = false) MultipartFile certificates) throws Exception {
        if (pdf.getSize() > PocPdfVerifier.MAX_PDF) throw new TlAcceptance.Rejected("PDF_SIZE_LIMIT");
        if (certificates != null && certificates.getSize() > 1024 * 1024) throw new TlAcceptance.Rejected("CERT_SIZE_LIMIT");
        TlAcceptance.Snapshot snapshot;
        try { snapshot = acceptance.capture(); } catch (TlAcceptance.Rejected e) { snapshot = null; }
        return runs.execute(pdf.getBytes(), certificates == null || certificates.isEmpty() ? new byte[0] : certificates.getBytes(), snapshot, at);
    }
    @GetMapping("/runs") public List<PocRunStore.Run> runs() throws Exception { return runs.list(); }
    @GetMapping("/runs/{id}") public PocRunStore.Run run(@PathVariable String id) throws Exception { return runs.read(id); }
    @GetMapping("/compare") public PocRunStore.Comparison compare(@RequestParam String baseline, @RequestParam String comparison) throws Exception {
        return runs.compare(baseline, comparison);
    }
    @ExceptionHandler(TlAcceptance.Rejected.class)
    ResponseEntity<Map<String, String>> rejected(TlAcceptance.Rejected e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, String>> failed(Exception e) {
        return ResponseEntity.internalServerError().body(Map.of("error", "PROCESSING_FAILED"));
    }
}
