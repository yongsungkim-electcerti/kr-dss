package com.electcerti.krdss.poc.rp.pdf;

import com.electcerti.krdss.tl.builder.KrTrustListBuilder;
import com.electcerti.krdss.tl.builder.KrTrustListXml;
import com.electcerti.krdss.tl.model.KrTrustList;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 로컬 PDF 서명·검증 화면이 참조하는 자료(인증서 · 트러스트스토어 · KR-TL) 적재기.
 *
 * <p>Whale PoC 는 저장소의 {@code output/certs} 에 있는 데모 자료를 그대로 쓴다.
 * 실행 위치가 저장소 루트(bootRun)일 수도, {@code output/poc}(배포 jar)일 수도 있어
 * 후보 경로를 순서대로 탐색한다.</p>
 */
@Component
public class PdfMaterials {

    private static final Logger log = LoggerFactory.getLogger(PdfMaterials.class);


    private final Path certsDir;
    private final String tlSignerFile;
    private final char[] tlSignerPassword;
    private final KrTrustListBuilder trustListBuilder = new KrTrustListBuilder();
    private final KrTrustListXmlSigner xmlSigner;

    public PdfMaterials(KrTrustListXmlSigner xmlSigner,
                        @Value("${krdss.rp.pdf.certs-dir:}") String configuredDir,
                        @Value("${krdss.rp.pdf.tl-signer:kisa-tl-signer.p12}") String tlSignerFile,
                        @Value("${krdss.rp.pdf.tl-signer-password:111111}") String tlSignerPassword) {
        this.xmlSigner = xmlSigner;
        this.certsDir = resolveCertsDir(configuredDir);
        this.tlSignerFile = tlSignerFile;
        this.tlSignerPassword = tlSignerPassword.toCharArray();
        log.info("[PDF] 데모 자료 디렉터리: {}", certsDir != null ? certsDir.toAbsolutePath() : "(없음)");
    }

    /**
     * 데모 자료 디렉터리를 찾는다.
     *
     * <p>설정값이 있으면 그것만 쓰고, 없으면 실행 위치에 따라 달라지는 후보를 훑는다.
     * bootRun 은 저장소 루트에서, 배포 jar 는 output/poc 에서 뜨기 때문이다.</p>
     */
    private static Path resolveCertsDir(String configuredDir) {
        if (configuredDir != null && !configuredDir.isBlank()) {
            Path explicit = Path.of(configuredDir);
            return Files.isDirectory(explicit) ? explicit : null;
        }
        List<Path> candidates = List.of(
                Path.of("output", "certs"),
                Path.of("..", "certs"),
                Path.of("certs"));
        for (Path candidate : candidates) {
            if (Files.isDirectory(candidate)) {
                return candidate.normalize();
            }
        }
        return null;
    }

    /**
     * 트러스트스토어에서 신뢰 앵커 인증서를 뽑는다.
     *
     * <p>PKCS#12 · JKS · PEM · DER 중 무엇이 와도 되도록 키스토어로 먼저 시도하고
     * 실패하면 인증서 파일로 읽는다. 화면에서 형식을 따로 고르게 하지 않기 위함이다.</p>
     */
    public List<X509Certificate> loadTrustAnchors(byte[] data, char[] password) {
        List<X509Certificate> fromKeyStore = fromKeyStore(data, password);
        if (!fromKeyStore.isEmpty()) {
            return fromKeyStore;
        }
        List<X509Certificate> fromEncoded = fromEncoded(data);
        if (!fromEncoded.isEmpty()) {
            return fromEncoded;
        }
        throw new IllegalArgumentException(
                "트러스트스토어에서 X.509 인증서를 읽지 못했습니다. PKCS#12(.p12)·JKS·PEM(.crt/.pem)을 지원합니다.");
    }

    private static List<X509Certificate> fromKeyStore(byte[] data, char[] password) {
        for (String type : List.of("PKCS12", "JKS")) {
            // 인증서만 담긴 스토어는 비밀번호가 없을 수 있어 두 경우를 모두 시도한다.
            char[][] passwords = { password, null };
            for (char[] candidate : passwords) {
                List<X509Certificate> certificates = tryKeyStore(data, type, candidate);
                if (!certificates.isEmpty()) {
                    return certificates;
                }
            }
        }
        return List.of();
    }

    private static List<X509Certificate> tryKeyStore(byte[] data, String type, char[] password) {
        try {
            KeyStore keyStore = KeyStore.getInstance(type);
            keyStore.load(new ByteArrayInputStream(data), password);
            List<X509Certificate> certificates = new ArrayList<>();
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                collect(keyStore.getCertificate(alias), certificates);
                Certificate[] chain = keyStore.getCertificateChain(alias);
                if (chain != null) {
                    for (Certificate certificate : chain) {
                        collect(certificate, certificates);
                    }
                }
            }
            return certificates;
        } catch (Exception e) {
            // 형식·비밀번호가 맞지 않는 조합 — 호출부가 다음 후보로 넘어간다.
            return List.of();
        }
    }

    private static void collect(Certificate certificate, List<X509Certificate> into) {
        if (certificate instanceof X509Certificate x509 && !into.contains(x509)) {
            into.add(x509);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<X509Certificate> fromEncoded(byte[] data) {
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            Collection<X509Certificate> parsed =
                    (Collection<X509Certificate>) factory.generateCertificates(new ByteArrayInputStream(data));
            return List.copyOf(parsed);
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * KR-TL 파일을 읽고 전자서명까지 검증한다.
     *
     * <p>서명된 KR-TL(JWS)과 미서명 KR-TL(순수 JSON)을 모두 받는다. 어느 쪽인지와
     * 서명 유효 여부는 결과에 담겨 오며, 판정 반영은 검증기가 맡는다.</p>
     */
    public KrTrustListDocument loadTrustList(byte[] document) {
        return KrTrustListDocument.parse(document);
    }

    /**
     * {@code output/certs} 의 CA 인증서로 예시 KR-TL 을 만든다.
     *
     * <p>저장소에는 검증에 그대로 넣을 수 있는 KR-TL 파일이 없다. 화면에서 내려받아
     * 곧바로 검증 입력으로 쓸 수 있도록 생성해 준다.</p>
     */
    public KrTrustList sampleTrustList() {
        if (certsDir == null) {
            throw new IllegalStateException("output/certs 디렉터리를 찾지 못해 예시 KR-TL 을 만들 수 없습니다.");
        }
        List<KrTrustList.TrustService> services = new ArrayList<>();
        try (Stream<Path> files = Files.list(certsDir)) {
            List<Path> caFiles = files.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return name.endsWith(".crt") && name.contains("ca");
                    })
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path caFile : caFiles) {
                for (X509Certificate certificate : fromEncoded(Files.readAllBytes(caFile))) {
                    services.add(new KrTrustList.TrustService(
                            "http://uri.etsi.org/TrstSvc/Svctype/CA/QC",
                            certificate.getSubjectX500Principal().getName(),
                            KrTrustList.ServiceStatus.GRANTED,
                            certificate.getNotBefore().toInstant(),
                            encoded(certificate)));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Instant now = Instant.now();
        return new KrTrustList(
                new KrTrustList.SchemeInformation(1, "KISA (Whale PoC 예시)", now, now.plus(Duration.ofDays(90))),
                List.of(new KrTrustList.TrustServiceProvider("가상 인정사업자 (Whale PoC)", services)));
    }

    /**
     * 예시 KR-TL 을 만들고 신뢰목록 서명자 키로 전자서명해 배포본(JWS)을 반환한다.
     *
     * <p>certs 를 재발급하면 CA 도 서명자도 바뀌므로, 파일을 저장해 두기보다 필요할 때
     * 새로 만들어 내려받게 한다. 그래야 신뢰목록 내용과 실제 인증체인이 어긋나지 않는다.</p>
     */
    public byte[] signedSampleTrustList(KrTrustListDocument.Format format) {
        KrTrustList trustList = trustListBuilder.build(sampleTrustList());
        SigningMaterial signer = loadTlSigner();
        byte[] signed = switch (format) {
            case XML -> xmlSigner.sign(KrTrustListXml.toXml(trustList), signer.privateKey(), signer.chain());
            case JSON_JWS -> trustListBuilder.sign(trustList, signer.privateKey(), signer.chain());
            case JSON_PLAIN -> throw new IllegalArgumentException("미서명 KR-TL 은 발급하지 않습니다.");
        };
        log.info("[PDF] KR-TL 서명 완료 — 형식={}, 서명자={}, 서비스 {}건", format.label(),
                signer.chain().get(0).getSubjectX500Principal().getName(),
                trustList.trustServiceProviders().stream().mapToInt(p -> p.services().size()).sum());
        return signed;
    }

    /** 신뢰목록 서명자 키스토어(기본 kisa-tl-signer.p12)를 연다. */
    private SigningMaterial loadTlSigner() {
        if (certsDir == null) {
            throw new IllegalStateException("output/certs 디렉터리를 찾지 못해 KR-TL 을 서명할 수 없습니다.");
        }
        Path keystorePath = certsDir.resolve(tlSignerFile);
        if (!Files.isRegularFile(keystorePath)) {
            throw new IllegalStateException("KR-TL 서명자 키스토어가 없습니다: " + keystorePath.toAbsolutePath());
        }
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(new ByteArrayInputStream(Files.readAllBytes(keystorePath)), tlSignerPassword);
            Enumeration<String> aliases = keyStore.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                if (!keyStore.isKeyEntry(alias)) {
                    continue;
                }
                PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, tlSignerPassword);
                Certificate[] chain = keyStore.getCertificateChain(alias);
                if (privateKey == null || chain == null || chain.length == 0) {
                    continue;
                }
                List<X509Certificate> certificates = new ArrayList<>();
                for (Certificate certificate : chain) {
                    certificates.add((X509Certificate) certificate);
                }
                return new SigningMaterial(privateKey, List.copyOf(certificates));
            }
        } catch (Exception e) {
            throw new IllegalStateException(
                    "KR-TL 서명자 키스토어를 열지 못했습니다(" + keystorePath.getFileName() + "): " + e.getMessage(), e);
        }
        throw new IllegalStateException("KR-TL 서명자 키스토어에 개인키 항목이 없습니다: " + keystorePath.getFileName());
    }

    private record SigningMaterial(PrivateKey privateKey, List<X509Certificate> chain) {
    }

    private static byte[] encoded(X509Certificate certificate) {
        try {
            return certificate.getEncoded();
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException("인증서 인코딩 실패", e);
        }
    }

}
