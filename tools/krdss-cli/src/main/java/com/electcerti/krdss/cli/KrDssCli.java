package com.electcerti.krdss.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * KR-DSS CLI — 서명/검증 수동 실증·QA 도구.
 */
@Command(
        name = "krdss",
        mixinStandardHelpOptions = true,
        version = "krdss 0.1.0",
        description = "KR-DSS 전자서명 생성/검증 CLI",
        subcommands = {KrDssCli.SignCommand.class, KrDssCli.VerifyCommand.class}
)
public class KrDssCli implements Callable<Integer> {

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new KrDssCli()).execute(args);
        System.exit(exitCode);
    }

    @Command(name = "sign", description = "전자문서에 서명을 생성한다.")
    static class SignCommand implements Callable<Integer> {
        @Option(names = {"-i", "--in"}, required = true, description = "입력 문서 경로")
        String input;

        @Option(names = {"-f", "--format"}, defaultValue = "PADES", description = "포맷 (XADES|CADES|PADES|JADES|HADES|MADES)")
        String format;

        @Override
        public Integer call() {
            System.out.printf("[sign] format=%s in=%s (구현 예정)%n", format, input);
            return 0;
        }
    }

    @Command(name = "verify", description = "서명문서를 검증한다.")
    static class VerifyCommand implements Callable<Integer> {
        @Option(names = {"-i", "--in"}, required = true, description = "서명문서 경로")
        String input;

        @Override
        public Integer call() {
            System.out.printf("[verify] in=%s (구현 예정)%n", input);
            return 0;
        }
    }
}
