package com.electcerti.krdss.poc.rp.tlpoc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** main 비관리 영역의 실행별 불변 기록. 실제 결과를 클라이언트가 지정할 수 없다. */
public final class PocRunStore {
    private static final java.util.concurrent.ThreadPoolExecutor WORKER = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.SynchronousQueue<>(),
            task -> { var thread = new Thread(task, "tl-poc-verifier"); thread.setDaemon(true); return thread; });
    private final Path root;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    public record Run(String id, Instant executedAt, String inputHash, String certificatesHash,
            String policy, String engine, Instant evaluationTime, String timeSource,
            String tlSequence, String tlHash, Instant tlAcceptedAt, String execution,
            PocPdfVerifier.Result result, String error) { }
    public record Comparison(boolean comparable, List<String> mismatches, Run baseline, Run comparison,
            boolean tlChanged, List<String> differences) { }
    public PocRunStore(Path root) throws Exception { this.root = root; Files.createDirectories(root); }
    public Run execute(byte[] pdf, byte[] certs, TlAcceptance.Snapshot tl, Instant at) throws Exception {
        if (pdf.length > PocPdfVerifier.MAX_PDF || certs.length > 1024 * 1024) throw new TlAcceptance.Rejected("INPUT_SIZE_LIMIT");
        for (byte[] input : List.of(pdf, certs)) {
            if (java.util.regex.Pattern.compile("-----BEGIN [^-]*PRIVATE KEY-----")
                    .matcher(new String(input, java.nio.charset.StandardCharsets.ISO_8859_1)).find())
                throw new TlAcceptance.Rejected("SECRET_CONTENT");
        }
        String id = UUID.randomUUID().toString(); Instant started = Instant.now();
        PocPdfVerifier.Result result = null; String error = null;
        java.util.concurrent.Future<PocPdfVerifier.Result> task = null;
        try {
            task = WORKER.submit(() -> new PocPdfVerifier().verify(pdf, tl, at, certs.length == 0 ? List.of() : TlAcceptance.certificates(certs)));
            result = task.get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) { task.cancel(true); error = "VERIFY_TIMEOUT"; }
        catch (java.util.concurrent.RejectedExecutionException e) { error = "VERIFIER_BUSY"; }
        catch (java.util.concurrent.ExecutionException e) { error = e.getCause() instanceof TlAcceptance.Rejected ? e.getCause().getMessage() : "VERIFY_ERROR"; }
        catch (InterruptedException e) { if (task != null) task.cancel(true); Thread.currentThread().interrupt(); error = "VERIFY_CANCELLED"; }
        var run = new Run(id, started, TlAcceptance.hash(pdf), TlAcceptance.hash(certs), PocPdfVerifier.POLICY,
                engine(), at, "TEST_SPECIFIED", tl == null ? null : tl.sequence(), tl == null ? null : tl.sha256(),
                tl == null ? null : tl.acceptedAt(), error == null ? "COMPLETED" : "ERROR", result, error);
        Path temp = Files.createTempDirectory(root, ".pending-");
        Files.write(temp.resolve("input.pdf"), pdf, StandardOpenOption.CREATE_NEW);
        Files.write(temp.resolve("certificates.bin"), certs, StandardOpenOption.CREATE_NEW);
        if (tl != null) Files.write(temp.resolve("tl.xml"), tl.xml(), StandardOpenOption.CREATE_NEW);
        byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(run);
        Files.write(temp.resolve("result.json"), json, StandardOpenOption.CREATE_NEW);
        Files.writeString(temp.resolve("result.sha256"), TlAcceptance.hash(json), StandardOpenOption.CREATE_NEW);
        Files.move(temp, root.resolve(id), StandardCopyOption.ATOMIC_MOVE);
        return run;
    }
    public List<Run> list() throws Exception {
        var runs = new ArrayList<Run>();
        try (var paths = Files.list(root)) {
            for (var path : paths.filter(p -> p.getFileName().toString().matches("[a-f0-9-]{36}")).toList())
                runs.add(read(path.getFileName().toString()));
        }
        runs.sort(Comparator.comparing(Run::executedAt).reversed()); return List.copyOf(runs);
    }
    public byte[] tl(String id) throws Exception {
        var run = read(id);
        if (run.tlHash() == null) return null;
        byte[] bytes = Files.readAllBytes(root.resolve(id).resolve("tl.xml"));
        if (!run.tlHash().equals(TlAcceptance.hash(bytes))) throw new TlAcceptance.Rejected("RESULT_INTEGRITY_ERROR");
        return bytes;
    }
    public Run read(String id) throws Exception {
        if (!id.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")) throw new TlAcceptance.Rejected("RUN_ID_INVALID");
        Path dir = root.resolve(id);
        if (!Files.isRegularFile(dir.resolve("result.json"))) throw new TlAcceptance.Rejected("RUN_NOT_FOUND");
        byte[] json = Files.readAllBytes(dir.resolve("result.json"));
        if (!TlAcceptance.hash(json).equals(Files.readString(dir.resolve("result.sha256")))) throw new TlAcceptance.Rejected("RESULT_INTEGRITY_ERROR");
        Run run = mapper.readValue(json, Run.class);
        if (!run.id().equals(id) || !run.inputHash().equals(TlAcceptance.hash(Files.readAllBytes(dir.resolve("input.pdf"))))
                || !run.certificatesHash().equals(TlAcceptance.hash(Files.readAllBytes(dir.resolve("certificates.bin"))))
                || (run.tlHash() != null && !run.tlHash().equals(TlAcceptance.hash(Files.readAllBytes(dir.resolve("tl.xml"))))))
            throw new TlAcceptance.Rejected("RESULT_INTEGRITY_ERROR");
        return run;
    }
    public Comparison compare(String a, String b) throws Exception {
        var x = read(a); var y = read(b); var mismatch = new ArrayList<String>();
        if (!x.inputHash().equals(y.inputHash())) mismatch.add("서명 문서");
        if (!x.certificatesHash().equals(y.certificatesHash())) mismatch.add("추가 인증서");
        if (!x.policy().equals(y.policy())) mismatch.add("정책");
        if (!x.engine().equals(y.engine())) mismatch.add("검증 엔진");
        if (!x.evaluationTime().equals(y.evaluationTime()) || !x.timeSource().equals(y.timeSource())) mismatch.add("평가시각");
        if (!"COMPLETED".equals(x.execution()) || !"COMPLETED".equals(y.execution())) mismatch.add("실행 오류");
        var differences = new ArrayList<String>();
        if (x.result() != null && y.result() != null) {
            if (!x.result().integrity().equals(y.result().integrity())) differences.add("문서 무결성");
            if (!x.result().chain().equals(y.result().chain())) differences.add("인증 경로");
            if (!x.result().trust().equals(y.result().trust())) differences.add("TL 신뢰 판단");
            if (!Objects.equals(x.result().serviceStatus(), y.result().serviceStatus())) differences.add("서비스 상태");
            if (!Objects.equals(x.result().service(), y.result().service())) differences.add("선택 서비스");
        }
        return new Comparison(mismatch.isEmpty(), List.copyOf(mismatch), x, y,
                !Objects.equals(x.tlHash(), y.tlHash()), List.copyOf(differences));
    }
    static String engine() throws Exception {
        var bytes = new java.io.ByteArrayOutputStream();
        for (var type : List.of(PocPdfVerifier.class, TlAcceptance.class,
                com.electcerti.krdss.tl.model.TrustServiceTimeline.class, org.apache.pdfbox.pdmodel.PDDocument.class,
                org.bouncycastle.cms.CMSSignedData.class)) {
            try (var stream = type.getResourceAsStream(type.getSimpleName() + ".class")) { bytes.write(stream.readAllBytes()); }
            bytes.write(String.valueOf(type.getPackage().getImplementationVersion()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return "poc-v1-" + TlAcceptance.hash(bytes.toByteArray());
    }
}
