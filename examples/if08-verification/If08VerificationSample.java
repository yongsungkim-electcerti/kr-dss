import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 이용기관용 IF-08 흐름 참조 샘플(Java 17 이상, 프로젝트 기준 Java 21).
 * 암호 알고리즘 구현이 아니다. 실제 모듈은 CryptoVerifier에 연결한다.
 * 각 검사는 선택한 PDF 서명 하나와 동일한 TL 파일을 대상으로 수행한다.
 */
public final class If08VerificationSample {
    public enum Outcome { PASSED, FAILED, INDETERMINATE, NOT_RUN }

    public enum Stage {
        TL_SIGNATURE("TL 서명과 사전 설정된 KISA TL 신뢰앵커 확인"),
        TL_ACCEPTANCE("TL 형식·순번·시각·이력 정합성 확인"),
        DOCUMENT_SIGNATURE("선택한 PDF 서명의 암호학적 무결성과 문서 변경 확인"),
        CERTIFICATE_PATH("서명자 인증서에서 TL에 등재된 서비스까지 경로 확인"),
        REVOCATION("OCSP/CRL의 진위·유효기간과 인증서 폐지 상태 확인"),
        SERVICE_HISTORY("정책상 판정 시각에 해당 서비스가 인정 상태인지 확인");

        public final String description;
        Stage(String description) { this.description = description; }
    }

    /**
     * 경로의 파일은 실행 동안 바뀌지 않는 로컬 스냅숏이어야 한다.
     * trustConfiguration은 TL 서명 앵커/중간 인증서와 검증 정책을 가리킨다.
     * 기존 수용본이 없으면 previousAcceptedTl은 null이다.
     * 서명 시각은 입력받지 않는다. 암호 모듈이 선택 서명에서 추출해 정책에 전달한다.
     */
    public record Input(Path signedPdf, int signatureIndex, Path candidateTl,
                        Path previousAcceptedTl, Path trustConfiguration,
                        Instant verificationTime) {
        public Input {
            Objects.requireNonNull(signedPdf);
            Objects.requireNonNull(candidateTl);
            Objects.requireNonNull(trustConfiguration);
            Objects.requireNonNull(verificationTime);
            if (signatureIndex < 0) throw new IllegalArgumentException("signatureIndex >= 0");
        }
    }

    /** code는 안정된 기계 판독 값, explanation은 이용기관 개발자를 위한 설명이다. */
    public record Check(Outcome outcome, String code, String explanation) {
        public Check {
            Objects.requireNonNull(outcome);
            Objects.requireNonNull(code);
            Objects.requireNonNull(explanation);
            if (outcome == Outcome.NOT_RUN) {
                throw new IllegalArgumentException("NOT_RUN은 흐름 제어기가 생성한다");
            }
        }
    }

    public record Step(Stage stage, Outcome outcome, String code, String explanation) {}
    public record Report(Outcome outcome, List<Step> steps) {
        public Report { steps = List.copyOf(steps); }
    }

    /**
     * 실제 DSS/KR-DSS/암호모듈을 연결할 경계. 각 메서드는 증거를 확인한 결과만 반환한다.
     * TL 서명용 앵커를 PDF 서명자 앵커로 자동 사용하지 않는다.
     * 단일 실행에 연결된 어댑터들은 같은 파싱 결과·서명 식별자·경로를 공유해야 한다.
     */
    public interface CryptoVerifier {
        Check verifyTlSignature(Input input) throws Exception;
        Check verifyDocumentSignature(Input input) throws Exception;
        Check verifyCertificatePath(Input input) throws Exception;
        Check verifyRevocation(Input input) throws Exception;
    }

    /**
     * KR-TL 의미 판정 경계. 관리자 화면이 허용한 오류도 여기서는 반드시 검사한다.
     * 이력 판정은 CryptoVerifier가 확인한 동일 서명·동일 경로와 연결한다.
     * claimed signing time과 신뢰 가능한 시각 증거는 구별한다.
     */
    public interface TrustPolicy {
        Check acceptTl(Input input) throws Exception;
        Check evaluateServiceHistory(Input input) throws Exception;
    }

    public static Report verify(Input input, CryptoVerifier crypto, TrustPolicy policy) {
        Objects.requireNonNull(input);
        Objects.requireNonNull(crypto);
        Objects.requireNonNull(policy);
        List<Step> steps = new ArrayList<>();
        Outcome result = Outcome.PASSED;

        for (Stage stage : Stage.values()) {
            if (result != Outcome.PASSED) {
                steps.add(new Step(stage, Outcome.NOT_RUN, "DEPENDENCY_NOT_PASSED",
                        "앞 단계가 통과하지 않아 실행하지 않았습니다."));
                continue;
            }
            Check check;
            try {
                check = Objects.requireNonNull(switch (stage) {
                    case TL_SIGNATURE -> crypto.verifyTlSignature(input);
                    case TL_ACCEPTANCE -> policy.acceptTl(input);
                    case DOCUMENT_SIGNATURE -> crypto.verifyDocumentSignature(input);
                    case CERTIFICATE_PATH -> crypto.verifyCertificatePath(input);
                    case REVOCATION -> crypto.verifyRevocation(input);
                    case SERVICE_HISTORY -> policy.evaluateServiceHistory(input);
                }, "검증 결과 누락");
            } catch (Exception failure) {
                // 예외를 성공 또는 암호학적 위조 판정으로 바꾸지 않는다.
                check = new Check(Outcome.INDETERMINATE, "VERIFIER_ERROR",
                        "검증 모듈 실행 실패. 연결된 모듈의 진단 정보를 확인해야 합니다.");
            }
            steps.add(new Step(stage, check.outcome(), check.code(), check.explanation()));
            result = check.outcome();
        }
        return new Report(result, steps);
    }

    /** 실제 모듈 미연결 예시. 데모 실행으로 검증 성공을 가장하지 않는다. */
    public static final class UnconfiguredCrypto implements CryptoVerifier {
        private Check missing() {
            return new Check(Outcome.INDETERMINATE, "CRYPTO_NOT_CONFIGURED",
                    "실제 암호 검증 모듈이 연결되지 않았습니다.");
        }
        public Check verifyTlSignature(Input input) { return missing(); }
        public Check verifyDocumentSignature(Input input) { return missing(); }
        public Check verifyCertificatePath(Input input) { return missing(); }
        public Check verifyRevocation(Input input) { return missing(); }
    }

    public static void main(String[] args) {
        TrustPolicy unconfiguredPolicy = new TrustPolicy() {
            public Check acceptTl(Input input) { return missing(); }
            public Check evaluateServiceHistory(Input input) { return missing(); }
            private Check missing() {
                return new Check(Outcome.INDETERMINATE, "POLICY_NOT_CONFIGURED",
                        "KR-TL 검증 정책이 연결되지 않았습니다.");
            }
        };
        Input input = new Input(Path.of("signed.pdf"), 0, Path.of("kr-tl.xml"),
                null, Path.of("trust-config"), Instant.now());
        Report report = verify(input, new UnconfiguredCrypto(), unconfiguredPolicy);
        System.out.println("전체 결과: " + report.outcome());
        report.steps().forEach(step -> System.out.printf("%s | %s | %s | %s%n",
                step.stage(), step.outcome(), step.code(), step.explanation()));
    }
}
