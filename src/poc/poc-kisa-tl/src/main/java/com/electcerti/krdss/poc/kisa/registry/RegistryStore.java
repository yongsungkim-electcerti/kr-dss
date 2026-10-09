package com.electcerti.krdss.poc.kisa.registry;

import com.electcerti.krdss.poc.kisa.registry.RegistryException.Code;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/**
 * 초안 revision 파일 저장소. 단일 WAS·로컬 파일 시스템 전제.
 *
 * <ul>
 *   <li>{@code registry/revisions/r-NNNNNNNN.json} — 불변 초안. 다음 저장은 새 파일</li>
 *   <li>{@code publication/state.json} — 활성 초안 revision과 그 파일 해시. 교체가 저장 확정 지점</li>
 *   <li>{@code .lock} — 같은 저장 루트를 쓰는 두 번째 프로세스의 기동을 막는 잠금</li>
 * </ul>
 *
 * <p>state.json에 기록되지 않은 revision 파일(교체 전 중단의 잔여물)은 활성 초안으로 쓰지 않는다.
 * 상태를 해석·검증할 수 없으면 최대 revision을 자동 선택하지 않고 쓰기를 중단한다.</p>
 */
public final class RegistryStore implements AutoCloseable {

    private static final Pattern REVISION_FILE = Pattern.compile("r-(\\d{8,})\\.json");
    static final int STATE_VERSION = 1;

    /** state.json 내용. 발행 단계에서 필드가 추가되면 stateVersion을 올린다. */
    public record State(int stateVersion, long activeDraftRevision, String activeDraftSha256, Instant updatedAt) {
    }

    /** 저장소 가용 상태. available=false이면 관리 쓰기를 받지 않는다. */
    public record Health(boolean available, String reason, String root) {
    }

    /** 저장 이력 목록의 한 줄. onActiveChain=false는 확정되지 않은 잔여 파일이다. */
    public record RevisionSummary(long revision, Long parentRevision, Instant savedAt, String change,
            boolean active, boolean onActiveChain) {
    }

    private final Path root;
    private final Path revisions;
    private final Path stateFile;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final ReentrantLock writeLock = new ReentrantLock();
    private final FileChannel lockChannel;
    private final FileLock processLock;

    private volatile RegistryDraft current;
    private volatile Health health;

    /** 시험용 장애 주입: revision 기록 후 state 교체 직전에 실행된다. */
    Runnable beforeStateSwap = () -> { };

    private RegistryStore(Path root, Clock clock) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        this.revisions = this.root.resolve("registry").resolve("revisions");
        this.stateFile = this.root.resolve("publication").resolve("state.json");
        this.clock = clock;
        this.mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
        Files.createDirectories(revisions);
        Files.createDirectories(stateFile.getParent());
        this.lockChannel = FileChannel.open(this.root.resolve(".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = lockChannel.tryLock();
        } catch (OverlappingFileLockException e) {
            lock = null;
        }
        if (lock == null) {
            lockChannel.close();
            throw new IllegalStateException("다른 관리 프로세스가 저장 루트를 사용 중입니다: " + this.root);
        }
        this.processLock = lock;
    }

    /** 저장 루트를 잠그고 활성 초안을 복원한다. 최초 기동이면 빈 초안 revision 1을 만든다. */
    public static RegistryStore open(Path root, Clock clock) {
        RegistryStore store;
        try {
            store = new RegistryStore(root, clock);
        } catch (IOException e) {
            throw new IllegalStateException("관리 저장 루트를 준비할 수 없습니다: " + root, e);
        }
        store.recover();
        return store;
    }

    private void recover() {
        try {
            checkAtomicMove();
            if (!Files.exists(stateFile)) {
                if (!listRevisionFiles().isEmpty()) {
                    unavailable("state.json 없이 revision 파일만 있습니다. 활성 초안을 자동 선택하지 않습니다.");
                    return;
                }
                var initial = RegistryDraft.initial(clock.instant());
                commitFiles(initial);
                health = new Health(true, null, root.toString());
                return;
            }
            State state = mapper.readValue(stateFile.toFile(), State.class);
            if (state.stateVersion() != STATE_VERSION) {
                unavailable("지원하지 않는 state 버전: " + state.stateVersion());
                return;
            }
            Path file = revisionFile(state.activeDraftRevision());
            if (!Files.exists(file)) {
                unavailable("활성 revision 파일이 없습니다: " + file.getFileName());
                return;
            }
            byte[] bytes = Files.readAllBytes(file);
            if (!CertificateInspector.sha256Hex(bytes).equals(state.activeDraftSha256())) {
                unavailable("활성 revision 파일의 해시가 state.json과 다릅니다: " + file.getFileName());
                return;
            }
            var draft = mapper.readValue(bytes, RegistryDraft.class);
            if (draft.revision() != state.activeDraftRevision()
                    || draft.schemaVersion() != RegistryDraft.SCHEMA_VERSION) {
                unavailable("활성 revision 내용이 state.json과 맞지 않습니다.");
                return;
            }
            current = draft;
            health = new Health(true, null, root.toString());
        } catch (AtomicMoveNotSupportedException e) {
            unavailable("저장 위치가 원자적 파일 교체를 지원하지 않습니다.");
        } catch (Exception e) {
            unavailable("상태를 복원할 수 없습니다: " + e.getMessage());
        }
    }

    private void unavailable(String reason) {
        current = null;
        health = new Health(false, reason, root.toString());
    }

    public Health health() {
        return health;
    }

    public Path root() {
        return root;
    }

    /** 검증된 활성 초안. 읽기는 잠금 없이 불변 스냅숏을 본다. */
    public RegistryDraft current() {
        var draft = current;
        if (draft == null) throw new RegistryException(Code.STATE_UNAVAILABLE, health.reason());
        return draft;
    }

    /**
     * 기대 revision이 현재와 같을 때만 편집을 적용해 새 revision으로 확정한다.
     * 내용이 바뀌지 않으면 새 revision을 만들지 않는다. 실패하면 이전 상태를 유지한다.
     */
    public RegistryDraft update(long expectedRevision, String change, UnaryOperator<RegistryDraft> edit) {
        Objects.requireNonNull(change, "change");
        writeLock.lock();
        try {
            var base = current();
            if (base.revision() != expectedRevision) {
                throw new RegistryException(Code.REVISION_CONFLICT,
                        "다른 편집이 먼저 저장되었습니다(현재 revision " + base.revision()
                                + ", 요청 " + expectedRevision + "). 다시 불러온 뒤 편집하십시오.");
            }
            var edited = edit.apply(base);
            if (Objects.equals(edited.scheme(), base.scheme())
                    && Objects.equals(edited.providers(), base.providers())
                    && Objects.equals(edited.baselineIssuanceId(), base.baselineIssuanceId())) {
                return base;
            }
            long next = Math.max(base.revision(), maxRevisionOnDisk()) + 1;
            var draft = new RegistryDraft(RegistryDraft.SCHEMA_VERSION, next, base.revision(), clock.instant(),
                    change, edited.baselineIssuanceId(), edited.scheme(), edited.providers());
            commitFiles(draft);
            return draft;
        } finally {
            writeLock.unlock();
        }
    }

    private void commitFiles(RegistryDraft draft) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(draft);
            Path target = revisionFile(draft.revision());
            if (Files.exists(target)) {
                throw new RegistryException(Code.STORAGE_FAILED, "revision 파일이 이미 있습니다: " + target.getFileName());
            }
            writeDurably(revisions.resolve(target.getFileName() + ".tmp"), bytes, target);
            beforeStateSwap.run();
            var state = new State(STATE_VERSION, draft.revision(), CertificateInspector.sha256Hex(bytes),
                    clock.instant());
            writeDurably(stateFile.resolveSibling("state.json.tmp"), mapper.writeValueAsBytes(state), stateFile);
            current = draft;
        } catch (RegistryException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryException(Code.STORAGE_FAILED, "초안을 저장하지 못했습니다. 기존 초안을 유지합니다.", e);
        }
    }

    /** 임시 파일에 기록·동기화한 뒤 원자 교체한다. 교체 전 실패하면 대상 파일은 그대로다. */
    private static void writeDurably(Path temp, byte[] bytes, Path target) throws IOException {
        try (var channel = FileChannel.open(temp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        }
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private void checkAtomicMove() throws IOException {
        Path probe = root.resolve(".probe.tmp");
        Path moved = root.resolve(".probe");
        Files.write(probe, new byte[] {1});
        try {
            Files.move(probe, moved, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(probe);
            Files.deleteIfExists(moved);
        }
    }

    /** 최근 저장 이력. 활성 초안에서 parent를 따라간 연결과 잔여 파일을 구별한다. */
    public List<RevisionSummary> revisions(int limit) {
        var draft = current();
        var summaries = new ArrayList<RevisionSummary>();
        try {
            var files = listRevisionFiles();
            files.sort(Comparator.comparingLong(RegistryStore::revisionNumber).reversed());
            var drafts = new ArrayList<RegistryDraft>();
            for (var file : files) {
                try {
                    drafts.add(mapper.readValue(file.toFile(), RegistryDraft.class));
                } catch (IOException e) {
                    // 해석 불가 잔여 파일은 목록에서 생략한다. 활성 초안은 기동 시 검증했다.
                }
            }
            var chain = new HashSet<Long>();
            Long cursor = draft.revision();
            var byRevision = new HashMap<Long, RegistryDraft>();
            for (var d : drafts) byRevision.put(d.revision(), d);
            while (cursor != null && chain.add(cursor)) {
                var d = byRevision.get(cursor);
                cursor = d == null ? null : d.parentRevision();
            }
            for (var d : drafts) {
                if (summaries.size() >= limit) break;
                summaries.add(new RevisionSummary(d.revision(), d.parentRevision(), d.savedAt(), d.change(),
                        d.revision() == draft.revision(), chain.contains(d.revision())));
            }
        } catch (IOException e) {
            throw new RegistryException(Code.STORAGE_FAILED, "저장 이력을 읽지 못했습니다.", e);
        }
        return summaries;
    }

    private long maxRevisionOnDisk() {
        try {
            return listRevisionFiles().stream().mapToLong(RegistryStore::revisionNumber).max().orElse(0);
        } catch (IOException e) {
            throw new RegistryException(Code.STORAGE_FAILED, "revision 목록을 읽지 못했습니다.", e);
        }
    }

    private List<Path> listRevisionFiles() throws IOException {
        try (var stream = Files.list(revisions)) {
            return new ArrayList<>(stream
                    .filter(p -> REVISION_FILE.matcher(p.getFileName().toString()).matches())
                    .toList());
        }
    }

    private static long revisionNumber(Path file) {
        var matcher = REVISION_FILE.matcher(file.getFileName().toString());
        if (!matcher.matches()) throw new IllegalArgumentException(file.toString());
        return Long.parseLong(matcher.group(1));
    }

    private Path revisionFile(long revision) {
        return revisions.resolve(String.format("r-%08d.json", revision));
    }

    @Override
    public void close() {
        try {
            processLock.release();
            lockChannel.close();
        } catch (IOException e) {
            // 프로세스 종료 시 운영체제가 잠금을 해제한다.
        }
    }
}
