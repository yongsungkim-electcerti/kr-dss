package com.electcerti.krdss.cli;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateFactory;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.openssl.jcajce.JcaPKCS8Generator;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * 프로파일(JSON)에 적은 발급 관계대로 PoC 인증체계 전체를 생성한다(설계 27).
 *
 * <p>노드마다 key.pem·cert.pem·chain.pem·meta.json·README.md를 만들고, 키쌍 일치·발급자 서명·
 * Root까지의 경로·용도 확장을 검사한다. 생성은 임시 폴더에서 끝낸 뒤 출력 폴더로 옮긴다.
 * 출력 폴더가 이미 있으면 덮어쓰지 않는다.</p>
 */
@Command(name = "tree", description = "프로파일(JSON)의 발급 관계대로 PoC 인증체계 전체를 생성·검증한다.")
public class PkiTreeCommand implements Callable<Integer> {

    static final String TOOL = "krdss-cli cert tree";
    static final ASN1ObjectIdentifier TSL_SIGNING = new ASN1ObjectIdentifier("0.4.0.2231.3.0");
    static final ASN1ObjectIdentifier OCSP_NOCHECK = new ASN1ObjectIdentifier("1.3.6.1.5.5.7.48.1.5");

    @Option(names = {"-f", "--profile"}, required = true, description = "생성 프로파일 JSON")
    Path profilePath;

    @Option(names = {"-o", "--out"}, required = true, description = "출력 폴더(없어야 함). 예: runtime/tl-management/pki")
    Path out;

    enum Role { ROOT, CA, OCSP, TSA, TL_SIGNER }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Profile {
        public String setId;
        public String keyAlg = "EC";
        public int keySize = 256;
        public Map<Role, Integer> validityDays = new LinkedHashMap<>();
        public List<Group> groups = new ArrayList<>();
        public List<Provider> providers = new ArrayList<>();
        public List<Node> nodes = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Group {
        public String dir;
        public String title;
        public String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Provider {
        public String providerId;
        public String name;
        public String domain;
        public boolean tlListed;
        public String dir;
        public String description;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Node {
        public String id;
        public Role role;
        public String subject;
        public String issuer;
        public String dir;
        public String providerId;
        public boolean tlListed;
        public Integer trustPointLevel;
        public String purpose;
    }

    /** 생성 결과(메모리). */
    record Issued(Node node, KeyPair keys, X509Certificate cert, List<X509Certificate> chain,
            Map<String, Boolean> checks) {
    }

    @Override
    public Integer call() throws Exception {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        Path target = out.toAbsolutePath().normalize();
        if (Files.exists(target)) {
            System.err.println("[pki] 출력 폴더가 이미 있습니다. 기존 키를 덮어쓰지 않습니다: " + target);
            return 2;
        }
        byte[] profileBytes = Files.readAllBytes(profilePath);
        var mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        var profile = mapper.readValue(profileBytes, Profile.class);
        validate(profile);

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var issued = new LinkedHashMap<String, Issued>();
        for (var node : profile.nodes) {
            issued.put(node.id, issue(profile, node, issued, now));
        }
        // 전 노드 생성 후 경로 검증(Root를 신뢰앵커로 PKIX 검증)
        for (var item : issued.values()) {
            item.checks().put("pathToRoot", validatePath(item.chain(), now));
        }
        boolean allPassed = issued.values().stream()
                .allMatch(i -> i.checks().values().stream().allMatch(Boolean::booleanValue));

        Path staging = target.resolveSibling(target.getFileName() + ".staging-" + now.getEpochSecond());
        Files.createDirectories(staging);
        String profileSha = sha256(profileBytes);
        for (var item : issued.values()) {
            writeNode(staging, item, issued, mapper);
        }
        writeProviders(staging, profile, mapper);
        writeManifest(staging, profile, issued, mapper, now, profileSha, allPassed);
        writeGroupReadmes(staging, profile, issued, now, profileSha);
        if (!allPassed) {
            System.err.println("[pki] 검증 실패 노드가 있어 출력 폴더로 옮기지 않았습니다: " + staging);
            issued.values().forEach(i -> System.err.printf("  %-6s %s%n", i.node().id, i.checks()));
            return 1;
        }
        Files.createDirectories(target.getParent());
        Files.move(staging, target);
        System.out.printf("[pki] 생성 완료 · 세트 %s · 노드 %d개 · 검증 통과%n", profile.setId, issued.size());
        System.out.println("  -> " + target);
        return 0;
    }

    private static void validate(Profile profile) {
        if (profile.setId == null || profile.setId.isBlank()) throw new IllegalArgumentException("setId 필요");
        var seen = new LinkedHashMap<String, Node>();
        for (var node : profile.nodes) {
            if (node.id == null || node.role == null || node.subject == null || node.dir == null) {
                throw new IllegalArgumentException("노드에는 id·role·subject·dir가 필요합니다: " + node.id);
            }
            if (seen.containsKey(node.id)) throw new IllegalArgumentException("중복 노드 ID: " + node.id);
            boolean root = node.role == Role.ROOT;
            if (root != (node.issuer == null)) {
                throw new IllegalArgumentException("ROOT만 issuer가 없어야 합니다: " + node.id);
            }
            if (!root) {
                var issuer = seen.get(node.issuer);
                if (issuer == null) throw new IllegalArgumentException("발급자가 앞에 정의되지 않음: " + node.id);
                if (issuer.role != Role.ROOT && issuer.role != Role.CA) {
                    throw new IllegalArgumentException("발급자는 ROOT 또는 CA여야 합니다: " + node.id);
                }
                if (issuer.role == Role.CA && node.role == Role.CA) {
                    throw new IllegalArgumentException("CA는 pathLen 0이므로 하위 CA를 발급하지 않습니다: " + node.id);
                }
            }
            if (!profile.validityDays.containsKey(node.role)) {
                throw new IllegalArgumentException("유효기간 미정의 역할: " + node.role);
            }
            seen.put(node.id, node);
        }
    }

    private static Issued issue(Profile profile, Node node, Map<String, Issued> issued, Instant now)
            throws Exception {
        KeyPair keys = keyPair(profile.keyAlg, profile.keySize);
        X500Name subject = X500Name.getInstance(new X500Principal(node.subject).getEncoded());
        Issued issuer = node.issuer == null ? null : issued.get(node.issuer);
        X500Name issuerName = issuer == null ? subject
                : X500Name.getInstance(issuer.cert().getSubjectX500Principal().getEncoded());
        var signingKey = issuer == null ? keys.getPrivate() : issuer.keys().getPrivate();

        var builder = new JcaX509v3CertificateBuilder(issuerName, new BigInteger(96, new SecureRandom()),
                Date.from(now.minus(5, ChronoUnit.MINUTES)),
                Date.from(now.plus(profile.validityDays.get(node.role), ChronoUnit.DAYS)),
                subject, keys.getPublic());
        var ext = new JcaX509ExtensionUtils();
        builder.addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(keys.getPublic()));
        if (issuer != null) {
            builder.addExtension(Extension.authorityKeyIdentifier, false,
                    ext.createAuthorityKeyIdentifier(issuer.cert()));
        }
        switch (node.role) {
            case ROOT -> {
                builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
                builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            }
            case CA -> {
                builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(0));
                builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
            }
            case OCSP -> {
                builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
                builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
                builder.addExtension(Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_OCSPSigning));
                builder.addExtension(OCSP_NOCHECK, false, DERNull.INSTANCE);
            }
            case TSA -> {
                builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
                builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
                builder.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_timeStamping));
            }
            case TL_SIGNER -> {
                builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
                builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
                builder.addExtension(Extension.extendedKeyUsage, false,
                        new ExtendedKeyUsage(KeyPurposeId.getInstance(TSL_SIGNING)));
            }
        }
        String alg = signingKey.getAlgorithm().startsWith("EC") ? "SHA256withECDSA" : "SHA256withRSA";
        var cert = new JcaX509CertificateConverter().setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(builder.build(new JcaContentSignerBuilder(alg)
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME).build(signingKey)));

        var chain = new ArrayList<X509Certificate>();
        chain.add(cert);
        if (issuer != null) chain.addAll(issuer.chain());

        var checks = new LinkedHashMap<String, Boolean>();
        checks.put("keyPairMatches", keyPairMatches(keys, cert));
        checks.put("issuerSignature", verifies(cert, issuer == null ? cert : issuer.cert()));
        checks.put("usageMatchesRole", usageMatches(node.role, cert));
        return new Issued(node, keys, cert, chain, checks);
    }

    private static KeyPair keyPair(String alg, int size) throws Exception {
        if ("EC".equalsIgnoreCase(alg)) {
            var generator = KeyPairGenerator.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            generator.initialize(new ECGenParameterSpec(switch (size) {
                case 256 -> "secp256r1";
                case 384 -> "secp384r1";
                case 521 -> "secp521r1";
                default -> throw new IllegalArgumentException("EC 곡선 크기: 256/384/521");
            }), new SecureRandom());
            return generator.generateKeyPair();
        }
        if ("RSA".equalsIgnoreCase(alg)) {
            var generator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME);
            generator.initialize(size, new SecureRandom());
            return generator.generateKeyPair();
        }
        throw new IllegalArgumentException("지원하지 않는 키 알고리즘: " + alg);
    }

    private static boolean keyPairMatches(KeyPair keys, X509Certificate cert) throws Exception {
        String alg = keys.getPrivate().getAlgorithm().startsWith("EC") ? "SHA256withECDSA" : "SHA256withRSA";
        byte[] probe = new byte[32];
        new SecureRandom().nextBytes(probe);
        var signer = Signature.getInstance(alg);
        signer.initSign(keys.getPrivate());
        signer.update(probe);
        byte[] signature = signer.sign();
        var verifier = Signature.getInstance(alg);
        verifier.initVerify(cert.getPublicKey());
        verifier.update(probe);
        return verifier.verify(signature);
    }

    private static boolean verifies(X509Certificate cert, X509Certificate issuer) {
        try {
            cert.verify(issuer.getPublicKey());
            return cert.getIssuerX500Principal().equals(issuer.getSubjectX500Principal());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean usageMatches(Role role, X509Certificate cert) throws Exception {
        boolean[] ku = cert.getKeyUsage();
        List<String> eku = cert.getExtendedKeyUsage();
        int pathLen = cert.getBasicConstraints();
        return switch (role) {
            case ROOT -> pathLen == Integer.MAX_VALUE && ku[5] && ku[6] && eku == null;
            case CA -> pathLen == 0 && ku[5] && ku[6] && eku == null;
            case OCSP -> pathLen == -1 && ku[0] && !ku[5] && eku != null && eku.equals(List.of("1.3.6.1.5.5.7.3.9"));
            case TSA -> pathLen == -1 && ku[0] && !ku[5] && eku != null && eku.equals(List.of("1.3.6.1.5.5.7.3.8"))
                    && cert.getCriticalExtensionOIDs().contains(Extension.extendedKeyUsage.getId());
            case TL_SIGNER -> pathLen == -1 && ku[0] && !ku[5] && eku != null
                    && eku.equals(List.of(TSL_SIGNING.getId()));
        };
    }

    private static boolean validatePath(List<X509Certificate> chain, Instant now) {
        try {
            var root = chain.get(chain.size() - 1);
            if (chain.size() == 1) return verifies(root, root);
            var factory = CertificateFactory.getInstance("X.509");
            var path = factory.generateCertPath(chain.subList(0, chain.size() - 1));
            var params = new PKIXParameters(Set.of(new TrustAnchor(root, null)));
            params.setRevocationEnabled(false);
            params.setDate(Date.from(now));
            CertPathValidator.getInstance("PKIX").validate(path, params);
            return true;
        } catch (Exception e) {
            System.err.println("[pki] 경로 검증 실패: " + chain.get(0).getSubjectX500Principal() + " — " + e.getMessage());
            return false;
        }
    }

    // ------------------------------------------------------------ 파일 기록

    private static void writeNode(Path root, Issued item, Map<String, Issued> issued,
            ObjectMapper mapper) throws Exception {
        Path dir = root.resolve(item.node().dir);
        Files.createDirectories(dir);
        try (var writer = new JcaPEMWriter(Files.newBufferedWriter(dir.resolve("key.pem")))) {
            writer.writeObject(new JcaPKCS8Generator(item.keys().getPrivate(), null));
        }
        Files.writeString(dir.resolve("cert.pem"), pem(List.of(item.cert())));
        Files.writeString(dir.resolve("chain.pem"), pem(item.chain()));
        mapper.writeValue(dir.resolve("meta.json").toFile(), meta(item, issued, root, dir));
        Files.writeString(dir.resolve("README.md"), nodeReadme(item, issued));
    }

    private static Map<String, Object> meta(Issued item, Map<String, Issued> issued, Path root, Path dir) throws Exception {
        var node = item.node();
        var cert = item.cert();
        var m = new LinkedHashMap<String, Object>();
        m.put("certificateId", node.id);
        m.put("issuerCertificateId", node.issuer);
        m.put("providerId", node.providerId);
        m.put("role", node.role);
        m.put("purpose", node.purpose);
        m.put("tlListed", node.tlListed);
        m.put("trustPointLevel", node.trustPointLevel);
        String rel = root.relativize(dir).toString().replace('\\', '/');
        m.put("files", Map.of("key", rel + "/key.pem", "cert", rel + "/cert.pem", "chain", rel + "/chain.pem"));
        m.put("subject", cert.getSubjectX500Principal().getName(X500Principal.RFC2253));
        m.put("issuer", cert.getIssuerX500Principal().getName(X500Principal.RFC2253));
        m.put("serialNumber", cert.getSerialNumber().toString(16));
        m.put("notBefore", cert.getNotBefore().toInstant().toString());
        m.put("notAfter", cert.getNotAfter().toInstant().toString());
        m.put("sha256", sha256(cert.getEncoded()));
        m.put("publicKeyAlgorithm", cert.getPublicKey().getAlgorithm());
        m.put("publicKeySha256", sha256(cert.getPublicKey().getEncoded()));
        m.put("subjectKeyIdentifier", HexFormat.of().formatHex(new JcaX509ExtensionUtils()
                .createSubjectKeyIdentifier(cert.getPublicKey()).getKeyIdentifier()));
        m.put("chainIds", chainIds(node, issued));
        m.put("verification", item.checks());
        return m;
    }

    private static List<String> chainIds(Node node, Map<String, Issued> issued) {
        var ids = new ArrayList<String>();
        for (Node n = node; n != null; n = n.issuer == null ? null : issued.get(n.issuer).node()) ids.add(n.id);
        return ids;
    }

    private static void writeProviders(Path root, Profile profile, ObjectMapper mapper) throws Exception {
        for (var provider : profile.providers) {
            Path dir = root.resolve(provider.dir);
            Files.createDirectories(dir);
            var m = new LinkedHashMap<String, Object>();
            m.put("providerId", provider.providerId);
            m.put("name", provider.name);
            m.put("domain", provider.domain);
            m.put("tlListed", provider.tlListed);
            m.put("description", provider.description);
            m.put("nodes", profile.nodes.stream().filter(n -> provider.providerId.equals(n.providerId))
                    .map(n -> Map.of("certificateId", n.id, "role", n.role.name(), "tlListed", n.tlListed,
                            "dir", n.dir))
                    .toList());
            mapper.writeValue(dir.resolve("provider.json").toFile(), m);
            Path subscribers = dir.resolve("subscribers");
            Files.createDirectories(subscribers);
            Files.writeString(subscribers.resolve("README.md"), "# " + provider.name + " 가입자 인증서\n\n"
                    + "가입자 인증서는 인증서 발급 서비스가 발급할 때 `<certificate-id>/` 폴더로 생성한다. "
                    + "수량·표본 시각은 PoC 시나리오에서 정한다. 이번 생성 세트에는 가입자 인증서가 없다.\n");
        }
    }

    private static void writeManifest(Path root, Profile profile, Map<String, Issued> issued, ObjectMapper mapper,
            Instant now, String profileSha, boolean allPassed) throws Exception {
        var m = new LinkedHashMap<String, Object>();
        m.put("setId", profile.setId);
        m.put("generatedAt", now.toString());
        m.put("tool", TOOL);
        m.put("profileSha256", profileSha);
        m.put("keyAlgorithm", profile.keyAlg + "-" + profile.keySize);
        m.put("privateKeyFormat", "PKCS#8 PEM, 암호화 없음 (PoC 로컬 전용, Git 비관리)");
        m.put("verificationPassed", allPassed);
        var nodes = new ArrayList<Map<String, Object>>();
        for (var item : issued.values()) {
            var n = new LinkedHashMap<String, Object>();
            n.put("certificateId", item.node().id);
            n.put("issuerCertificateId", item.node().issuer);
            n.put("providerId", item.node().providerId);
            n.put("role", item.node().role);
            n.put("dir", item.node().dir);
            n.put("subject", item.cert().getSubjectX500Principal().getName(X500Principal.RFC2253));
            n.put("sha256", sha256(item.cert().getEncoded()));
            n.put("tlListed", item.node().tlListed);
            n.put("trustPointLevel", item.node().trustPointLevel);
            nodes.add(n);
        }
        m.put("nodes", nodes);
        mapper.writeValue(root.resolve("manifest.json").toFile(), m);
    }

    // ------------------------------------------------------------ README

    private static String nodeReadme(Issued item, Map<String, Issued> issued) throws Exception {
        var node = item.node();
        var cert = item.cert();
        var sb = new StringBuilder();
        sb.append("# ").append(node.id).append(" — ").append(cn(cert)).append("\n\n");
        sb.append(node.purpose == null ? "" : node.purpose + "\n\n");
        sb.append("| 항목 | 값 |\n|---|---|\n");
        row(sb, "인증서 ID", node.id);
        row(sb, "역할", node.role.name());
        row(sb, "발급자 ID", node.issuer == null ? "자기 자신 (Root)" : node.issuer);
        row(sb, "TL 등재", node.tlListed ? "등재" + (node.trustPointLevel == null ? "" : " · 신뢰점 레벨 " + node.trustPointLevel) : "미등재");
        row(sb, "Subject", cert.getSubjectX500Principal().getName(X500Principal.RFC2253));
        row(sb, "Issuer", cert.getIssuerX500Principal().getName(X500Principal.RFC2253));
        row(sb, "Serial", cert.getSerialNumber().toString(16));
        row(sb, "유효기간 (UTC)", cert.getNotBefore().toInstant() + " ~ " + cert.getNotAfter().toInstant());
        row(sb, "SHA-256", "`" + sha256(cert.getEncoded()) + "`");
        row(sb, "공개키", cert.getPublicKey().getAlgorithm());
        row(sb, "KeyUsage", keyUsage(cert));
        row(sb, "ExtendedKeyUsage", cert.getExtendedKeyUsage() == null ? "—" : String.join(", ", cert.getExtendedKeyUsage()));
        row(sb, "BasicConstraints", cert.getBasicConstraints() < 0 ? "CA:false"
                : cert.getBasicConstraints() == Integer.MAX_VALUE ? "CA:true" : "CA:true, pathLen " + cert.getBasicConstraints());
        sb.append("\n## 파일\n\n");
        sb.append("- `key.pem` — 이 노드의 개인키 (PKCS#8, 암호화 없음). 내용을 문서·로그에 옮기지 않는다.\n");
        sb.append("- `cert.pem` — 인증서 1장\n");
        sb.append("- `chain.pem` — ").append(chainText(node, issued)).append("\n");
        sb.append("- `meta.json` — ID·발급 관계·지문·검증 결과\n\n");
        sb.append("## 생성 시 검증\n\n");
        item.checks().forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v ? "통과" : "실패").append("\n"));
        return sb.toString();
    }

    private static String chainText(Node node, Map<String, Issued> issued) {
        return String.join(" → ", chainIds(node, issued)) + " 순서의 공개 인증서";
    }

    private static void writeGroupReadmes(Path root, Profile profile, Map<String, Issued> issued, Instant now,
            String profileSha) throws Exception {
        var top = new StringBuilder();
        top.append("# KR-TL PoC 인증체계 (").append(profile.setId).append(")\n\n");
        top.append("생성: ").append(now).append(" · 도구: `").append(TOOL).append("` · 키: ")
                .append(profile.keyAlg).append("-").append(profile.keySize)
                .append(" · 프로파일 SHA-256 `").append(profileSha).append("`\n\n");
        top.append("설계: docs/design/poc/tl-management/pki/27-PoC-키파일-디렉터리-설계.md. ")
                .append("이 폴더는 Git 비관리 로컬 산출물이다. 개인키는 암호화 없이 저장되며 PoC 전용이다.\n\n");
        top.append("## 발급 관계 (★ = KR-TL 등재)\n\n```text\n").append(tree(profile, issued, null)).append("```\n\n");
        top.append("KISA TL RootCA(T-00)는 TL 서명 검증용 사전 신뢰앵커이고 TL에 싣지 않는다. ")
                .append("KISA 공동인증 RootCA(K-00)는 사업자 인증체계의 Root다. 두 체계 사이에 교차 인증은 없다. ")
                .append("UN은 TL 미등재 대조군이다.\n\n");
        top.append("## 사용처\n\n| 사용처 | 파일 |\n|---|---|\n")
                .append("| TL 서명 | `tl-signer/T-02-signer/key.pem`, `cert.pem` |\n")
                .append("| 이용기관 신뢰앵커 | `tl-signer/T-00-root/cert.pem` |\n")
                .append("| 이용기관 중간 인증서 | `tl-signer/T-01-ca/cert.pem` |\n")
                .append("| 관리 화면 서비스 등록 | ★ 노드의 `cert.pem` (개인키 불필요) |\n\n");
        top.append("## 다시 만들기\n\n기존 세트를 덮어쓰지 않는다. 새 세트는 다른 출력 폴더에 만들고 전환한다.\n\n```\n")
                .append("gradlew.bat :tools:krdss-cli:run --args=\"cert tree -f scripts/poc/tl-management/pki-profile.json -o <새 폴더>\"\n```\n");
        Files.writeString(root.resolve("README.md"), top.toString());

        for (var group : profile.groups) {
            Path dir = root.resolve(group.dir);
            Files.createDirectories(dir);
            var sb = new StringBuilder("# ").append(group.title).append("\n\n");
            if (group.description != null) sb.append(group.description).append("\n\n");
            sb.append("```text\n").append(tree(profile, issued, group.dir)).append("```\n");
            Files.writeString(dir.resolve("README.md"), sb.toString());
        }
    }

    /** 발급 관계 트리. prefixDir이 있으면 그 폴더에 속한 노드와 상위 노드만 표시한다. */
    private static String tree(Profile profile, Map<String, Issued> issued, String prefixDir) {
        var sb = new StringBuilder();
        for (var node : profile.nodes) {
            if (node.issuer == null && include(profile, node, prefixDir)) appendTree(sb, profile, issued, node, "", "", prefixDir);
        }
        return sb.toString();
    }

    private static boolean include(Profile profile, Node node, String prefixDir) {
        if (prefixDir == null || node.dir.startsWith(prefixDir + "/")) return true;
        return profile.nodes.stream().anyMatch(n -> node.id.equals(n.issuer) && include(profile, n, prefixDir));
    }

    private static void appendTree(StringBuilder sb, Profile profile, Map<String, Issued> issued, Node node,
            String lead, String childLead, String prefixDir) {
        sb.append(lead).append(cn(issued.get(node.id).cert())).append(" (").append(node.id).append(")")
                .append(node.tlListed ? "  ★" + (node.trustPointLevel == null ? "" : " 레벨 " + node.trustPointLevel) : "")
                .append("  ").append(node.dir).append("/\n");
        var children = profile.nodes.stream().filter(n -> node.id.equals(n.issuer))
                .filter(n -> include(profile, n, prefixDir)).toList();
        for (int i = 0; i < children.size(); i++) {
            boolean last = i == children.size() - 1;
            appendTree(sb, profile, issued, children.get(i), childLead + (last ? "└── " : "├── "),
                    childLead + (last ? "    " : "│   "), prefixDir);
        }
    }

    // ------------------------------------------------------------ 보조

    private static void row(StringBuilder sb, String key, String value) {
        sb.append("| ").append(key).append(" | ").append(value).append(" |\n");
    }

    private static String cn(X509Certificate cert) {
        for (var part : cert.getSubjectX500Principal().getName(X500Principal.RFC2253).split(",")) {
            if (part.startsWith("CN=")) return part.substring(3);
        }
        return cert.getSubjectX500Principal().getName();
    }

    private static String keyUsage(X509Certificate cert) {
        String[] names = {"digitalSignature", "nonRepudiation", "keyEncipherment", "dataEncipherment",
                "keyAgreement", "keyCertSign", "cRLSign", "encipherOnly", "decipherOnly"};
        boolean[] ku = cert.getKeyUsage();
        if (ku == null) return "—";
        var used = new ArrayList<String>();
        for (int i = 0; i < ku.length && i < names.length; i++) if (ku[i]) used.add(names[i]);
        return String.join(", ", used);
    }

    private static String pem(List<X509Certificate> certs) throws Exception {
        var text = new StringWriter();
        try (var writer = new JcaPEMWriter(text)) {
            for (var cert : certs) writer.writeObject(cert);
        }
        return text.toString();
    }

    static String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
