package com.electcerti.krdss.cli;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * 인증서 발급 도구 — KR-TL/서명 검증 실증에 쓰는 테스트 PKI 체인을 만든다.
 *
 * <p>지원 유형: 루트 CA(자가서명) · 중간 CA · 최종개체(EE).
 * 발급 결과는 PEM 인증서(.crt)와 PKCS#8 개인키(.key)로 저장한다.</p>
 *
 * <ul>
 *   <li>{@code cert gen}   — 단일 인증서 한 장 발급</li>
 *   <li>{@code cert chain} — 템플릿(JSON) 하나로 ROOT→SUB→EE 전체 체인을 일괄 발급</li>
 *   <li>{@code cert p12}   — 인증서+개인키(+체인)를 PKCS#12 키스토어로 묶기</li>
 * </ul>
 */
@Command(
        name = "cert",
        description = "테스트 인증서(CA/EE)를 발급한다.",
        subcommands = {CertCommand.GenCommand.class, CertCommand.ChainCommand.class, CertCommand.P12Command.class})
public class CertCommand implements Callable<Integer> {

    @Override
    public Integer call() {
        // 서브커맨드 없이 호출되면 사용법 출력.
        picocli.CommandLine.usage(this, System.out);
        return 0;
    }

    /** 발급 대상 유형. */
    enum CertType {ROOT, SUB, EE}

    // ====================================================================
    // cert gen — 단일 인증서 발급
    // ====================================================================
    @Command(name = "gen", description = "키쌍을 만들고 인증서 한 장을 발급한다.")
    static class GenCommand implements Callable<Integer> {

        @Option(names = {"-t", "--type"}, defaultValue = "EE",
                description = "발급 유형: ROOT(루트CA) | SUB(중간CA) | EE(최종개체). 기본 ${DEFAULT-VALUE}")
        CertType type;

        @Option(names = {"-s", "--subject"}, required = true,
                description = "주체 DN. 예: \"CN=KR Test Root CA,O=ELECTCERTI,C=KR\"")
        String subject;

        @Option(names = {"-o", "--out"}, required = true,
                description = "출력 경로 접두사. <prefix>.crt, <prefix>.key 두 파일을 생성")
        String out;

        @Option(names = "--issuer-cert", description = "발급자 인증서 PEM 경로 (SUB/EE 필수)")
        Path issuerCert;

        @Option(names = "--issuer-key", description = "발급자 개인키 PEM 경로 (SUB/EE 필수)")
        Path issuerKey;

        @Option(names = "--key-alg", defaultValue = "RSA",
                description = "키 알고리즘: RSA | EC. 기본 ${DEFAULT-VALUE}")
        String keyAlg;

        @Option(names = "--key-size", defaultValue = "2048",
                description = "RSA 키 비트수(2048/3072/4096) 또는 EC 곡선(256/384/521). 기본 ${DEFAULT-VALUE}")
        int keySize;

        @Option(names = "--days", defaultValue = "3650",
                description = "유효기간(일). 기본 ${DEFAULT-VALUE}")
        long days;

        @Option(names = "--san", description = "주체 대체 이름(SAN) DNS 항목. 여러 번 지정 가능")
        String[] sanDns;

        @Override
        public Integer call() throws Exception {
            registerProvider();
            boolean selfSigned = type == CertType.ROOT;
            if (!selfSigned && (issuerCert == null || issuerKey == null)) {
                System.err.printf("[cert] %s 발급에는 --issuer-cert 와 --issuer-key 가 필요합니다.%n", type);
                return 2;
            }

            KeyPair subjectKeys = generateKeyPair(keyAlg, keySize);
            X500Name subjectDn = new X500Name(subject);
            X500Name issuerDn;
            PrivateKey signingKey;
            if (selfSigned) {
                issuerDn = subjectDn;
                signingKey = subjectKeys.getPrivate();
            } else {
                X509Certificate issuer = readCertificate(issuerCert);
                issuerDn = new JcaX509CertificateHolder(issuer).getSubject();
                signingKey = readPrivateKey(issuerKey);
            }

            X509Certificate cert = issue(type, subjectDn, subjectKeys, issuerDn, signingKey, days, sanDns, out);
            printIssued(type, cert, out);
            return 0;
        }
    }

    // ====================================================================
    // cert chain — 템플릿 기반 ROOT→SUB→EE 일괄 발급
    // ====================================================================
    @Command(name = "chain",
            description = "템플릿(JSON) 하나로 ROOT→SUB→EE 전체 체인을 일괄 발급한다.")
    static class ChainCommand implements Callable<Integer> {

        @Option(names = {"-f", "--template"}, description = "체인 템플릿 JSON 경로")
        Path template;

        @Option(names = "--init", description = "샘플 템플릿 JSON을 지정 경로에 생성하고 종료")
        Path init;

        @Override
        public Integer call() throws Exception {
            ObjectMapper mapper = new ObjectMapper();

            // --init: 샘플 템플릿만 출력하고 종료
            if (init != null) {
                Path parent = init.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                mapper.writerWithDefaultPrettyPrinter().writeValue(init.toFile(), Template.sample());
                System.out.printf("[cert] 샘플 템플릿 생성 -> %s%n", init.toAbsolutePath());
                return 0;
            }

            if (template == null) {
                System.err.println("[cert] --template 또는 --init 중 하나가 필요합니다.");
                return 2;
            }

            registerProvider();
            Template tpl = mapper.readValue(template.toFile(), Template.class);
            if (tpl.root == null || tpl.sub == null || tpl.ee == null) {
                System.err.println("[cert] 템플릿에는 root, sub, ee 세 노드가 모두 있어야 합니다.");
                return 2;
            }

            Path outDir = Path.of(tpl.outDir == null ? "pki" : tpl.outDir);
            Files.createDirectories(outDir);

            // 1) ROOT (자가서명)
            NodeSpec root = tpl.root;
            KeyPair rootKeys = generateKeyPair(alg(tpl, root), size(tpl, root));
            X500Name rootDn = new X500Name(root.subject);
            String rootOut = outPrefix(outDir, root, "root");
            X509Certificate rootCert =
                    issue(CertType.ROOT, rootDn, rootKeys, rootDn, rootKeys.getPrivate(),
                            days(root, 7300), null, rootOut);

            // 2) SUB (ROOT가 발급)
            NodeSpec sub = tpl.sub;
            KeyPair subKeys = generateKeyPair(alg(tpl, sub), size(tpl, sub));
            X500Name subDn = new X500Name(sub.subject);
            String subOut = outPrefix(outDir, sub, "sub");
            X509Certificate subCert =
                    issue(CertType.SUB, subDn, subKeys, rootDn, rootKeys.getPrivate(),
                            days(sub, 3650), null, subOut);

            // 3) EE (SUB가 발급)
            NodeSpec ee = tpl.ee;
            KeyPair eeKeys = generateKeyPair(alg(tpl, ee), size(tpl, ee));
            X500Name eeDn = new X500Name(ee.subject);
            String eeOut = outPrefix(outDir, ee, "ee");
            X509Certificate eeCert =
                    issue(CertType.EE, eeDn, eeKeys, subDn, subKeys.getPrivate(),
                            days(ee, 825), ee.san, eeOut);

            // 4) 묶음 PEM — CA 체인(sub+root), EE 풀체인(ee+sub+root)
            Path caChain = outDir.resolve("ca-chain.pem");
            Path fullChain = outDir.resolve("ee-fullchain.pem");
            writePemAll(caChain, List.of(subCert, rootCert));
            writePemAll(fullChain, List.of(eeCert, subCert, rootCert));

            System.out.println("[cert] 체인 일괄 발급 완료");
            printIssued(CertType.ROOT, rootCert, rootOut);
            printIssued(CertType.SUB, subCert, subOut);
            printIssued(CertType.EE, eeCert, eeOut);
            System.out.printf("  ca-chain     -> %s%n", caChain.toAbsolutePath());
            System.out.printf("  ee-fullchain -> %s%n", fullChain.toAbsolutePath());
            return 0;
        }

        private static String alg(Template t, NodeSpec n) {
            return n.keyAlg != null ? n.keyAlg : (t.keyAlg != null ? t.keyAlg : "EC");
        }

        private static int size(Template t, NodeSpec n) {
            if (n.keySize != null) return n.keySize;
            return t.keySize != null ? t.keySize : 256;
        }

        private static long days(NodeSpec n, long fallback) {
            return n.days != null ? n.days : fallback;
        }

        private static String outPrefix(Path outDir, NodeSpec n, String defaultName) {
            String name = (n.out != null && !n.out.isBlank()) ? n.out : defaultName;
            return outDir.resolve(name).toString();
        }
    }

    // ====================================================================
    // cert p12 — 인증서+개인키(+체인)를 PKCS#12 키스토어로 묶기
    // ====================================================================
    @Command(name = "p12",
            description = "인증서·개인키(+체인)를 PKCS#12(.p12) 키스토어로 묶는다.")
    static class P12Command implements Callable<Integer> {

        @Option(names = {"-c", "--cert"}, required = true, description = "대상 인증서 PEM 경로")
        Path cert;

        @Option(names = {"-k", "--key"}, required = true, description = "대상 개인키 PEM 경로")
        Path key;

        @Option(names = "--chain", description = "상위 CA 체인 PEM(중간/루트, 여러 장 가능). 키스토어에 함께 저장")
        Path chain;

        @Option(names = {"-o", "--out"}, required = true, description = "출력 .p12 경로")
        Path out;

        @Option(names = {"-a", "--alias"}, description = "키 엔트리 별칭(미지정 시 인증서 CN 사용)")
        String alias;

        @Option(names = {"-p", "--password"}, required = true,
                description = "키스토어 및 개인키 보호 비밀번호")
        String password;

        @Option(names = "--append",
                description = "출력 키스토어가 있으면 새로 만들지 않고 엔트리를 추가한다(통합 키스토어용)")
        boolean append;

        @Override
        public Integer call() throws Exception {
            registerProvider();

            X509Certificate leaf = readCertificate(cert);
            PrivateKey privateKey = readPrivateKey(key);

            // 엔트리 인증서 체인: [leaf, (중간..., 루트)]
            List<X509Certificate> certChain = new ArrayList<>();
            certChain.add(leaf);
            if (chain != null) {
                certChain.addAll(readCertificates(chain));
            }
            Certificate[] chainArr = certChain.toArray(new Certificate[0]);

            String entryAlias = (alias != null && !alias.isBlank())
                    ? alias : cnOf(leaf);
            char[] pw = password.toCharArray();

            KeyStore ks = KeyStore.getInstance("PKCS12", BouncyCastleProvider.PROVIDER_NAME);
            // --append: 기존 키스토어가 있으면 불러와서 엔트리를 더한다.
            if (append && Files.exists(out)) {
                try (java.io.FileInputStream in = new java.io.FileInputStream(out.toFile())) {
                    ks.load(in, pw);
                }
            } else {
                ks.load(null, null);
            }
            ks.setKeyEntry(entryAlias, privateKey, pw, chainArr);

            Path parent = out.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
                ks.store(fos, pw);
            }

            System.out.printf("[cert] PKCS#12 키스토어 %s 완료%n", append ? "추가" : "생성");
            System.out.printf("    alias : %s%n", entryAlias);
            System.out.printf("    certs : %d (leaf + chain)%n", chainArr.length);
            System.out.printf("    total : %d entries%n", ks.size());
            System.out.printf("    out   -> %s%n", out.toAbsolutePath());
            return 0;
        }

        private static String cnOf(X509Certificate cert) throws Exception {
            org.bouncycastle.asn1.x500.RDN[] rdns =
                    new JcaX509CertificateHolder(cert).getSubject()
                            .getRDNs(org.bouncycastle.asn1.x500.style.BCStyle.CN);
            if (rdns.length > 0) {
                return org.bouncycastle.asn1.x500.style.IETFUtils.valueToString(rdns[0].getFirst().getValue());
            }
            return "key";
        }
    }

    // ====================================================================
    // 템플릿 POJO (JSON 매핑)
    // ====================================================================
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class Template {
        /** 출력 디렉터리. */
        public String outDir = "pki";
        /** 기본 키 알고리즘(RSA|EC) — 노드별로 재정의 가능. */
        public String keyAlg = "EC";
        /** 기본 키 크기 — 노드별로 재정의 가능. */
        public Integer keySize = 256;
        public NodeSpec root;
        public NodeSpec sub;
        public NodeSpec ee;

        static Template sample() {
            Template t = new Template();
            t.outDir = "build/pki";
            t.keyAlg = "EC";
            t.keySize = 256;

            t.root = new NodeSpec();
            t.root.subject = "CN=KR Test Root CA,O=ELECTCERTI,C=KR";
            t.root.days = 7300L;

            t.sub = new NodeSpec();
            t.sub.subject = "CN=KR Sub CA,O=ELECTCERTI,C=KR";
            t.sub.days = 3650L;

            t.ee = new NodeSpec();
            t.ee.subject = "CN=hong gildong,O=ELECTCERTI,C=KR";
            t.ee.days = 825L;
            t.ee.san = new String[]{"test.example.kr"};
            t.ee.keyAlg = "RSA";
            t.ee.keySize = 2048;
            return t;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class NodeSpec {
        /** 주체 DN (필수). */
        public String subject;
        /** 유효기간(일) — 미지정 시 유형별 기본값. */
        public Long days;
        /** EE 전용 SAN(DNS) 목록. */
        public String[] san;
        /** 키 알고리즘 재정의(선택). */
        public String keyAlg;
        /** 키 크기 재정의(선택). */
        public Integer keySize;
        /** 출력 파일명 접두사 재정의(선택, outDir 기준 상대). */
        public String out;
    }

    // ====================================================================
    // 발급 핵심 로직 (gen/chain 공용)
    // ====================================================================

    private static void registerProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /** 키쌍 생성 → 인증서 빌드 → {prefix}.crt/.key 저장 후 인증서 반환. */
    private static X509Certificate issue(
            CertType type, X500Name subject, KeyPair subjectKeys,
            X500Name issuerDn, PrivateKey signingKey,
            long days, String[] san, String outPrefix) throws Exception {

        X509Certificate cert =
                buildCertificate(subject, subjectKeys.getPublic(), issuerDn, signingKey, type, days, san);
        writePem(Path.of(outPrefix + ".crt"), cert);
        writePem(Path.of(outPrefix + ".key"), subjectKeys.getPrivate());
        return cert;
    }

    private static void printIssued(CertType type, X509Certificate cert, String outPrefix) throws Exception {
        System.out.printf("  [%s]%n", type);
        System.out.printf("    subject : %s%n", new JcaX509CertificateHolder(cert).getSubject());
        System.out.printf("    issuer  : %s%n", new JcaX509CertificateHolder(cert).getIssuer());
        System.out.printf("    serial  : %s%n", cert.getSerialNumber());
        System.out.printf("    valid   : %s ~ %s%n",
                cert.getNotBefore().toInstant(), cert.getNotAfter().toInstant());
        System.out.printf("    cert -> %s%n", Path.of(outPrefix + ".crt").toAbsolutePath());
        System.out.printf("    key  -> %s%n", Path.of(outPrefix + ".key").toAbsolutePath());
    }

    private static KeyPair generateKeyPair(String alg, int size) throws Exception {
        String a = alg.toUpperCase();
        if ("RSA".equals(a)) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
            kpg.initialize(size, new SecureRandom());
            return kpg.generateKeyPair();
        }
        if ("EC".equals(a)) {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            kpg.initialize(new ECGenParameterSpec(curveFor(size)), new SecureRandom());
            return kpg.generateKeyPair();
        }
        throw new IllegalArgumentException("지원하지 않는 키 알고리즘: " + alg + " (RSA|EC)");
    }

    private static String curveFor(int size) {
        return switch (size) {
            case 256 -> "secp256r1";
            case 384 -> "secp384r1";
            case 521 -> "secp521r1";
            default -> throw new IllegalArgumentException("EC 곡선 크기는 256/384/521 중 하나여야 합니다: " + size);
        };
    }

    private static X509Certificate buildCertificate(
            X500Name subject, PublicKey subjectKey,
            X500Name issuer, PrivateKey signingKey,
            CertType type, long days, String[] sanDns) throws Exception {

        Instant now = Instant.now();
        Date notBefore = Date.from(now);
        Date notAfter = Date.from(now.plus(days, ChronoUnit.DAYS));
        BigInteger serial = new BigInteger(96, new SecureRandom());

        JcaX509v3CertificateBuilder builder =
                new JcaX509v3CertificateBuilder(issuer, serial, notBefore, notAfter, subject, subjectKey);

        JcaX509ExtensionUtils extUtils = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.subjectKeyIdentifier, false,
                extUtils.createSubjectKeyIdentifier(subjectKey));

        boolean isCa = type != CertType.EE;
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(isCa));

        if (isCa) {
            builder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        } else {
            builder.addExtension(Extension.keyUsage, true,
                    new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));
            builder.addExtension(Extension.extendedKeyUsage, false,
                    new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));
            if (sanDns != null && sanDns.length > 0) {
                GeneralName[] names = new GeneralName[sanDns.length];
                for (int i = 0; i < sanDns.length; i++) {
                    names[i] = new GeneralName(GeneralName.dNSName, sanDns[i]);
                }
                builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(names));
            }
        }

        String sigAlg = signingKey.getAlgorithm().startsWith("EC") ? "SHA256withECDSA" : "SHA256withRSA";
        ContentSigner signer = new JcaContentSignerBuilder(sigAlg)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(signingKey);

        X509CertificateHolder holder = builder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }

    // ---- PEM 입출력 ----

    private static void writePem(Path path, Object obj) throws Exception {
        writePemAll(path, List.of(obj));
    }

    private static void writePemAll(Path path, List<?> objects) throws Exception {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (JcaPEMWriter writer = new JcaPEMWriter(new FileWriter(path.toFile()))) {
            for (Object o : objects) {
                writer.writeObject(o);
            }
        }
    }

    private static X509Certificate readCertificate(Path path) throws Exception {
        try (PEMParser parser = new PEMParser(new FileReader(path.toFile()))) {
            Object o = parser.readObject();
            if (o instanceof X509CertificateHolder holder) {
                return new JcaX509CertificateConverter()
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .getCertificate(holder);
            }
            throw new IllegalArgumentException("인증서 PEM이 아닙니다: " + path);
        }
    }

    /** 여러 장의 인증서가 이어 붙은 PEM(체인)을 모두 읽는다. */
    private static List<X509Certificate> readCertificates(Path path) throws Exception {
        List<X509Certificate> certs = new ArrayList<>();
        JcaX509CertificateConverter conv =
                new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
        try (PEMParser parser = new PEMParser(new FileReader(path.toFile()))) {
            Object o;
            while ((o = parser.readObject()) != null) {
                if (o instanceof X509CertificateHolder holder) {
                    certs.add(conv.getCertificate(holder));
                }
            }
        }
        if (certs.isEmpty()) {
            throw new IllegalArgumentException("인증서 PEM이 아닙니다: " + path);
        }
        return certs;
    }

    private static PrivateKey readPrivateKey(Path path) throws Exception {
        try (PEMParser parser = new PEMParser(new FileReader(path.toFile()))) {
            Object o = parser.readObject();
            JcaPEMKeyConverter conv = new JcaPEMKeyConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME);
            if (o instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo pki) {
                return conv.getPrivateKey(pki);
            }
            if (o instanceof org.bouncycastle.openssl.PEMKeyPair kp) {
                return conv.getKeyPair(kp).getPrivate();
            }
            throw new IllegalArgumentException("개인키 PEM이 아닙니다: " + path);
        }
    }
}
