package com.electcerti.krdss.poc.kisa.issuance;

import com.electcerti.krdss.poc.kisa.issuance.CandidateBuilder.Baseline;
import com.electcerti.krdss.poc.kisa.issuance.Issuance.Diagnostic;
import com.electcerti.krdss.poc.kisa.issuance.Issuance.Entry;
import com.electcerti.krdss.poc.kisa.issuance.Issuance.Manifest;
import com.electcerti.krdss.poc.kisa.issuance.Issuance.Plan;
import com.electcerti.krdss.poc.kisa.issuance.Issuance.Purpose;
import com.electcerti.krdss.poc.kisa.issuance.Issuance.Request;
import com.electcerti.krdss.poc.kisa.issuance.Issuance.RequestRecord;
import com.electcerti.krdss.poc.kisa.issuance.Issuance.Result;
import com.electcerti.krdss.poc.kisa.registry.RegistryException;
import com.electcerti.krdss.poc.kisa.registry.RegistryException.Code;
import com.electcerti.krdss.poc.kisa.registry.RegistryStore;
import com.electcerti.krdss.tl.builder.KrTrustListXml;
import com.electcerti.krdss.tl.builder.KrTrustListXmlSigner;
import com.electcerti.krdss.tl.model.KrTrustList;
import com.electcerti.krdss.tl.model.PocTrustListProfile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * TL 발행: 후보 편성 → 키 파일 서명 → 서명 자체 확인 → 불변 보관 → state 교체(확정).
 *
 * <p>설계 issuance-recovery.md를 따른다. state.json의 확정 목록에 있는 발행본만 성공이다. 같은 requestId 재요청은
 * 저장된 결과를 돌려주고 재서명하지 않는다. TRIAL은 정상 기준 이력·순번을 바꾸지 않는다.</p>
 */
public class IssuanceService {

    static final Duration DEFAULT_NEXT_UPDATE = Duration.ofDays(7);
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private final RegistryStore store;
    private final Path signerDir;
    private final Clock clock;
    private final Path issued;
    private final Path staging;
    private final Path requests;
    private final ObjectMapper mapper;
    private final byte[] profileJson;
    private final String profileSha256;

    /** 시험용 장애 주입: 발행본 이동 후 state 교체 직전에 실행된다. */
    Runnable beforeCommit = () -> { };

    public IssuanceService(RegistryStore store, Path signerDir, Clock clock) {
        this.store = store;
        this.signerDir = signerDir.toAbsolutePath().normalize();
        this.clock = clock;
        this.issued = store.root().resolve("issued");
        this.staging = store.root().resolve("staging");
        this.requests = store.root().resolve("requests");
        this.mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
        try {
            var profile = new LinkedHashMap<String, Object>();
            profile.put("profileId", "kr-tl-poc");
            profile.put("profileVersion", 1);
            profile.put("uriPrefix", PocTrustListProfile.PREFIX);
            profile.put("extensionNamespace", PocTrustListProfile.EXTENSION_NS);
            profile.put("signature", "XAdES-BASELINE-B enveloped, SHA-256");
            this.profileJson = mapper.writeValueAsBytes(profile);
            this.profileSha256 = sha256(profileJson);
            Files.createDirectories(issued);
            Files.createDirectories(staging);
            Files.createDirectories(requests);
        } catch (IOException e) {
            throw new IllegalStateException("발행 저장 위치를 준비할 수 없습니다.", e);
        }
        markInterrupted();
    }

    /** 기동 시: 확정되지 않은 진행 중 요청을 중단으로 종결한다. 자동 재개·삭제하지 않는다. */
    private void markInterrupted() {
        if (!store.health().available()) return;
        var state = store.state();
        try (var files = Files.list(requests)) {
            for (var file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                var record = mapper.readValue(file.toFile(), RequestRecord.class);
                if ("IN_PROGRESS".equals(record.status()) && state.byRequest(record.requestId()).isEmpty()) {
                    writeRequest(withStatus(record, "INTERRUPTED", Code.INTERRUPTED.name(),
                            "서버 중단으로 확정되지 않았습니다. 새 requestId로 다시 발행하십시오."));
                }
            }
        } catch (Exception e) {
            // 진단 기록 갱신 실패는 확정 상태에 영향이 없다.
        }
    }

    // ---------------------------------------------------------------- 계획(사전 검사)

    public Plan plan(Purpose purpose, String sequenceNumber, Instant issuedAt, Instant nextUpdate) {
        var draft = store.current();
        var baseline = baseline(store.state());
        var fixed = resolve(baseline, sequenceNumber, issuedAt, nextUpdate);
        var candidate = CandidateBuilder.build(draft, baseline, fixed.sequence, fixed.issuedAt, fixed.nextUpdate,
                clock.instant());
        var requested = purpose == null ? Purpose.BASELINE : purpose;
        String signerDetail;
        boolean signerReady;
        try {
            var keys = SignerKeys.load(signerDir);
            signerReady = true;
            signerDetail = keys.certificate().getSubjectX500Principal().getName() + " · 만료 "
                    + keys.certificate().getNotAfter().toInstant();
        } catch (RegistryException e) {
            signerReady = false;
            signerDetail = e.getMessage();
        }
        return new Plan(draft.revision(), store.state().baselineIssuanceId(), store.state().normalSequence(),
                CandidateBuilder.recommendedSequence(baseline).toString(), fixed.sequence.toString(),
                fixed.issuedAt, fixed.nextUpdate, requested, effective(requested, candidate.diagnostics()),
                candidate.diagnostics(), signerReady, signerDetail);
    }

    // ---------------------------------------------------------------- 발행

    public Result issue(Request request) {
        if (request.requestId() == null || !request.requestId().matches("[0-9a-fA-F-]{36}")) {
            throw new RegistryException(Code.INVALID_INPUT, "requestId는 UUID여야 합니다.");
        }
        if (request.expectedDraftRevision() == null) {
            throw new RegistryException(Code.INVALID_INPUT, "기대 초안 revision이 필요합니다.");
        }
        String digest = inputDigest(request);
        return store.locked(() -> {
            var state = store.state();
            var committed = state.byRequest(request.requestId());
            if (committed.isPresent()) {
                var manifest = readManifest(committed.get().issuanceId());
                if (!manifest.inputDigest().equals(digest)) {
                    throw new RegistryException(Code.REQUEST_CONFLICT, "같은 requestId에 다른 입력입니다. 기존 결과는 바뀌지 않습니다.");
                }
                return new Result(manifest, true);
            }
            var previous = readRequest(request.requestId());
            if (previous != null) {
                if (!previous.inputDigest().equals(digest)) {
                    throw new RegistryException(Code.REQUEST_CONFLICT, "같은 requestId에 다른 입력입니다.");
                }
                var code = previous.errorCode() == null ? Code.INTERRUPTED : Code.valueOf(previous.errorCode());
                throw new RegistryException(code, "이미 종결된 요청입니다: " + previous.errorMessage()
                        + " 새 requestId로 다시 시도하십시오.");
            }
            return issueNew(request, digest);
        });
    }

    private Result issueNew(Request request, String digest) {
        var draft = store.current();
        if (draft.revision() != request.expectedDraftRevision()) {
            throw new RegistryException(Code.REVISION_CONFLICT, "초안이 바뀌었습니다(현재 revision " + draft.revision()
                    + "). 다시 불러온 뒤 발행하십시오.");
        }
        var state = store.state();
        var baseline = baseline(state);
        var fixed = resolve(baseline, request.sequenceNumber(), request.issuedAt(), request.nextUpdate());
        var candidate = CandidateBuilder.build(draft, baseline, fixed.sequence, fixed.issuedAt, fixed.nextUpdate,
                clock.instant());
        var requested = request.purpose() == null ? Purpose.BASELINE : request.purpose();
        var purpose = effective(requested, candidate.diagnostics());
        String issuanceId = "iss-" + ID_TIME.format(clock.instant()) + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        var record = new RequestRecord(request.requestId(), "IN_PROGRESS", digest, request,
                fixed.sequence.toString(), fixed.issuedAt, fixed.nextUpdate, purpose, draft.revision(), issuanceId,
                null, null, clock.instant());
        writeRequest(record);
        try {
            var keys = SignerKeys.load(signerDir);
            byte[] signed;
            try {
                signed = KrTrustListXmlSigner.sign(KrTrustListXml.toXml(candidate.list()), keys.key(),
                        keys.keyInfoChain(), clock.instant());
            } catch (RuntimeException e) {
                throw new RegistryException(Code.SIGNING_FAILED, "TL 서명에 실패했습니다: " + e.getMessage(), e);
            }
            var check = KrTrustListXmlSigner.check(signed);
            if (!check.valid() || !keys.certificate().equals(check.signer())) {
                throw new RegistryException(Code.SIGNATURE_CHECK_FAILED, "서명 자체 확인 실패: " + check.detail());
            }
            String xmlSha = sha256(signed);
            var manifest = new Manifest(1, issuanceId, request.requestId(), digest, requested, purpose, "kr-tl",
                    fixed.sequence.toString(), fixed.issuedAt, fixed.nextUpdate, draft.revision(),
                    state.baselineIssuanceId(), xmlSha, signed.length, profileSha256,
                    keys.certificateSha256(),
                    keys.certificate().getSubjectX500Principal().getName(), true, check.detail(),
                    candidate.diagnostics(), clock.instant());
            storeFiles(request.requestId(), issuanceId, signed, candidate.list(), manifest);
            beforeCommit.run();
            store.commitIssuance(draft.revision(), new RegistryStore.CommittedIssuance(issuanceId,
                    request.requestId(), purpose.name(), fixed.sequence.toString(), xmlSha, clock.instant()),
                    purpose == Purpose.BASELINE);
            try {
                writeRequest(withStatus(record, "COMMITTED", null, null));
            } catch (RuntimeException ignored) {
                // 확정 권위는 state.json이다.
            }
            return new Result(manifest, false);
        } catch (RegistryException e) {
            failRequest(record, e.code(), e.getMessage());
            throw e;
        } catch (RuntimeException e) {
            failRequest(record, Code.STORAGE_FAILED, e.getMessage());
            throw new RegistryException(Code.STORAGE_FAILED, "발행을 확정하지 못했습니다: " + e.getMessage(), e);
        }
    }

    private void failRequest(RequestRecord record, Code code, String message) {
        try {
            writeRequest(withStatus(record, "FAILED", code.name(), message));
        } catch (RuntimeException ignored) {
            // 진단 기록 실패는 원래 오류를 가리지 않는다.
        }
    }

    /** staging에 모든 파일을 기록한 뒤 issued/&lt;issuanceId&gt;로 원자 이동한다. 기존 ID는 덮어쓰지 않는다. */
    private void storeFiles(String requestId, String issuanceId, byte[] signed, KrTrustList snapshot,
            Manifest manifest) {
        Path work = staging.resolve(requestId);
        Path target = issued.resolve(issuanceId);
        try {
            if (Files.exists(target)) throw new IOException("발행 ID가 이미 있습니다: " + issuanceId);
            Files.createDirectories(work);
            write(work.resolve("signed.xml"), signed);
            write(work.resolve("signed.sha256"), (manifest.xmlSha256() + "  signed.xml\n").getBytes(StandardCharsets.US_ASCII));
            write(work.resolve("snapshot.json"), mapper.writeValueAsBytes(snapshot));
            write(work.resolve("profile.json"), profileJson);
            write(work.resolve("manifest.json"), mapper.writeValueAsBytes(manifest));
            Files.move(work, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RegistryException(Code.STORAGE_FAILED, "발행본을 저장하지 못했습니다: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- 조회

    /** 확정 발행본(최신순)과 state에 없는 잔여 폴더. 각 항목의 파일 무결성을 확인한다. */
    public List<Entry> list() {
        var state = store.state();
        var entries = new ArrayList<Entry>();
        var known = new HashSet<String>();
        var committed = new ArrayList<>(state.committed());
        Collections.reverse(committed);
        for (var c : committed) {
            known.add(c.issuanceId());
            try {
                var manifest = readManifest(c.issuanceId());
                String detail = integrity(c.issuanceId(), c.xmlSha256());
                entries.add(new Entry(manifest, "COMMITTED", c.issuanceId().equals(state.baselineIssuanceId()),
                        c.issuanceId().equals(state.servedIssuanceId()), detail == null, detail));
            } catch (RuntimeException e) {
                entries.add(new Entry(null, "COMMITTED", false, false, false, c.issuanceId() + ": " + e.getMessage()));
            }
        }
        try (var dirs = Files.list(issued)) {
            for (var dir : dirs.filter(Files::isDirectory).toList()) {
                String id = dir.getFileName().toString();
                if (known.contains(id)) continue;
                Manifest manifest = null;
                try {
                    manifest = readManifest(id);
                } catch (RuntimeException ignored) {
                    // 미확정 잔여물의 manifest가 없을 수 있다.
                }
                entries.add(new Entry(manifest, "ORPHAN", false, false, false,
                        "state.json에 확정되지 않은 발행 폴더입니다(자동 제공·기준 승격 금지)."));
            }
        } catch (IOException e) {
            throw new RegistryException(Code.STORAGE_FAILED, "발행본 목록을 읽지 못했습니다.", e);
        }
        return entries;
    }

    /** 확정 발행본의 서명 XML 원문. 해시가 맞지 않으면 제공하지 않는다. */
    public byte[] signedXml(String issuanceId) {
        var committed = store.state().byIssuance(issuanceId)
                .orElseThrow(() -> new RegistryException(Code.NOT_FOUND, "확정된 발행본이 아닙니다: " + issuanceId));
        try {
            byte[] bytes = Files.readAllBytes(issued.resolve(issuanceId).resolve("signed.xml"));
            if (!sha256(bytes).equals(committed.xmlSha256())) {
                throw new RegistryException(Code.STATE_UNAVAILABLE, "발행본 파일이 손상되었습니다: " + issuanceId);
            }
            return bytes;
        } catch (IOException e) {
            throw new RegistryException(Code.STATE_UNAVAILABLE, "발행본 파일을 읽지 못했습니다: " + issuanceId, e);
        }
    }

    // ---------------------------------------------------------------- 제공본

    /** 현재 제공 선택. 선택이 없으면 servedIssuanceId·manifest가 null이다. */
    public record Publication(long publicationRevision, String servedIssuanceId, Manifest manifest) {
    }

    /** IF-07이 한 요청에서 쓰는 고정된 제공본: 확정 해시와 일치함을 확인한 바이트. */
    public record Served(String issuanceId, byte[] xml, String sha256) {
    }

    public Publication publication() {
        var state = store.state();
        var served = state.servedIssuanceId();
        return new Publication(state.publicationRevision(), served, served == null ? null : readManifest(served));
    }

    /**
     * 확정 발행본을 제공본으로 선택한다. 의미 오류·과거 순번은 거부 사유가 아니다. 파일이 손상되었으면 거부한다.
     * 재서명·새 발행이 아니다.
     */
    public Publication select(long expectedPublicationRevision, String issuanceId) {
        return store.locked(() -> {
            signedXml(issuanceId);
            store.selectServed(expectedPublicationRevision, issuanceId);
            return publication();
        });
    }

    /**
     * 공개 조회용. 상태 스냅숏 하나에서 제공 ID를 고정하고 그 바이트의 해시를 확정값과 대조한다.
     * 선택이 없으면 null. 저장소·파일 손상은 STATE_UNAVAILABLE이며 다른 발행본으로 대체하지 않는다.
     */
    public Served served() {
        var state = store.state();
        var id = state.servedIssuanceId();
        if (id == null) return null;
        var committed = state.byIssuance(id)
                .orElseThrow(() -> new RegistryException(Code.STATE_UNAVAILABLE, "제공 포인터가 확정 목록에 없습니다."));
        try {
            byte[] bytes = Files.readAllBytes(issued.resolve(id).resolve("signed.xml"));
            String sha = sha256(bytes);
            if (!sha.equals(committed.xmlSha256())) {
                throw new RegistryException(Code.STATE_UNAVAILABLE, "제공본 파일이 손상되었습니다: " + id);
            }
            return new Served(id, bytes, sha);
        } catch (IOException e) {
            throw new RegistryException(Code.STATE_UNAVAILABLE, "제공본 파일을 읽지 못했습니다: " + id, e);
        }
    }

    public Manifest manifest(String issuanceId) {
        store.state().byIssuance(issuanceId)
                .orElseThrow(() -> new RegistryException(Code.NOT_FOUND, "확정된 발행본이 아닙니다: " + issuanceId));
        return readManifest(issuanceId);
    }

    // ---------------------------------------------------------------- 내부

    private record Fixed(BigInteger sequence, Instant issuedAt, Instant nextUpdate) {
    }

    private Fixed resolve(Baseline baseline, String sequenceNumber, Instant issuedAt, Instant nextUpdate) {
        BigInteger sequence;
        if (sequenceNumber == null || sequenceNumber.isBlank()) {
            sequence = CandidateBuilder.recommendedSequence(baseline);
        } else {
            sequence = parseSequence(sequenceNumber);
        }
        var issue = issuedAt == null ? CandidateBuilder.defaultIssuedAt(clock.instant()) : issuedAt;
        var next = nextUpdate == null ? issue.plus(DEFAULT_NEXT_UPDATE) : nextUpdate;
        return new Fixed(sequence, issue, next);
    }

    private static BigInteger parseSequence(String text) {
        BigInteger sequence;
        try {
            sequence = new BigInteger(text.strip());
        } catch (NumberFormatException e) {
            throw new RegistryException(Code.INVALID_INPUT, "순번은 정수여야 합니다.");
        }
        if (sequence.signum() <= 0) throw new RegistryException(Code.INVALID_INPUT, "순번은 1 이상이어야 합니다.");
        return sequence;
    }

    private static Purpose effective(Purpose requested, List<Diagnostic> diagnostics) {
        return requested == Purpose.BASELINE && !diagnostics.isEmpty() ? Purpose.TRIAL : requested;
    }

    private Baseline baseline(RegistryStore.State state) {
        if (state.baselineIssuanceId() == null) return Baseline.NONE;
        var manifest = readManifest(state.baselineIssuanceId());
        try {
            var snapshot = mapper.readValue(issued.resolve(state.baselineIssuanceId()).resolve("snapshot.json").toFile(),
                    KrTrustList.class);
            return new Baseline(snapshot, new BigInteger(state.normalSequence()), manifest.issuedAt());
        } catch (IOException e) {
            throw new RegistryException(Code.STATE_UNAVAILABLE, "정상 기준 스냅숏을 읽지 못했습니다.", e);
        }
    }

    private String integrity(String issuanceId, String expectedSha) {
        try {
            byte[] bytes = Files.readAllBytes(issued.resolve(issuanceId).resolve("signed.xml"));
            return sha256(bytes).equals(expectedSha) ? null : "signed.xml 해시가 확정값과 다릅니다.";
        } catch (IOException e) {
            return "signed.xml을 읽지 못했습니다.";
        }
    }

    private Manifest readManifest(String issuanceId) {
        try {
            return mapper.readValue(issued.resolve(issuanceId).resolve("manifest.json").toFile(), Manifest.class);
        } catch (IOException e) {
            throw new RegistryException(Code.STATE_UNAVAILABLE, "발행본 manifest를 읽지 못했습니다: " + issuanceId, e);
        }
    }

    private RequestRecord readRequest(String requestId) {
        Path file = requests.resolve(requestId.toLowerCase() + ".json");
        if (!Files.exists(file)) return null;
        try {
            return mapper.readValue(file.toFile(), RequestRecord.class);
        } catch (IOException e) {
            throw new RegistryException(Code.STATE_UNAVAILABLE, "요청 기록을 읽지 못했습니다: " + requestId, e);
        }
    }

    private void writeRequest(RequestRecord record) {
        try {
            Path target = requests.resolve(record.requestId().toLowerCase() + ".json");
            Path temp = requests.resolve(record.requestId().toLowerCase() + ".json.tmp");
            write(temp, mapper.writeValueAsBytes(record));
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RegistryException(Code.STORAGE_FAILED, "요청 기록을 저장하지 못했습니다.", e);
        }
    }

    private RequestRecord withStatus(RequestRecord r, String status, String code, String message) {
        return new RequestRecord(r.requestId(), status, r.inputDigest(), r.request(), r.sequenceNumber(),
                r.issuedAt(), r.nextUpdate(), r.purpose(), r.draftRevision(), r.issuanceId(), code, message,
                clock.instant());
    }

    /** 명시 입력의 결정적 digest(버전 포함). 자동값은 포함하지 않는다. */
    private String inputDigest(Request request) {
        var input = new LinkedHashMap<String, Object>();
        input.put("v", 1);
        input.put("requestId", request.requestId().toLowerCase());
        input.put("expectedDraftRevision", request.expectedDraftRevision());
        input.put("purpose", request.purpose() == null ? null : request.purpose().name());
        input.put("sequenceNumber", request.sequenceNumber() == null || request.sequenceNumber().isBlank()
                ? null : parseSequence(request.sequenceNumber()).toString());
        input.put("issuedAt", request.issuedAt() == null ? null : request.issuedAt().toString());
        input.put("nextUpdate", request.nextUpdate() == null ? null : request.nextUpdate().toString());
        try {
            return sha256(new ObjectMapper().writeValueAsBytes(input));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(Path path, byte[] bytes) throws IOException {
        try (var channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
