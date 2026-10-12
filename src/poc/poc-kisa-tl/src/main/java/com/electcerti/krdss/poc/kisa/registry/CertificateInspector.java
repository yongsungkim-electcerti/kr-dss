package com.electcerti.krdss.poc.kisa.registry;

import com.electcerti.krdss.poc.kisa.registry.RegistryDraft.CertificateRecord;
import com.electcerti.krdss.poc.kisa.registry.RegistryException.Code;
import com.electcerti.krdss.tl.model.PocTrustListProfile.ServiceType;
import com.electcerti.krdss.tl.model.PocTrustListProfile.TrustPointLevel;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import javax.security.auth.x500.X500Principal;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;

/**
 * 관리자가 입력한 인증서(PEM 또는 DER)를 해석해 원문과 표시값을 만든다.
 *
 * <p>여기서의 경고는 입력 확인용 사전 검사이며 인증서 체인·인정 여부 검증이 아니다.</p>
 */
public final class CertificateInspector {

    static final int MAX_INPUT_BYTES = 64 * 1024;
    private static final String EKU_OCSP_SIGNING = "1.3.6.1.5.5.7.3.9";
    private static final String EKU_TIME_STAMPING = "1.3.6.1.5.5.7.3.8";

    private CertificateInspector() {
    }

    /** 해석한 인증서와 입력 확인 경고. */
    public record Inspected(X509Certificate certificate, CertificateRecord record, List<String> warnings) {
        public Inspected {
            warnings = List.copyOf(warnings);
        }
    }

    /** 한 입력에 PEM 묶음이면 여러 장, DER이면 한 장을 돌려준다. */
    public static List<X509Certificate> parse(byte[] input) {
        if (input == null || input.length == 0) {
            throw new RegistryException(Code.INVALID_INPUT, "인증서 입력이 비어 있습니다.");
        }
        if (input.length > MAX_INPUT_BYTES) {
            throw new RegistryException(Code.INVALID_INPUT, "인증서 입력이 너무 큽니다(최대 64KB).");
        }
        try {
            var factory = CertificateFactory.getInstance("X.509");
            if (isPem(input)) {
                var result = new ArrayList<X509Certificate>();
                for (var certificate : factory.generateCertificates(new ByteArrayInputStream(input))) {
                    result.add((X509Certificate) certificate);
                }
                if (result.isEmpty()) {
                    throw new RegistryException(Code.INVALID_INPUT, "PEM에서 인증서를 찾지 못했습니다.");
                }
                return List.copyOf(result);
            }
            var stream = new ByteArrayInputStream(input);
            var certificate = (X509Certificate) factory.generateCertificate(stream);
            if (stream.available() != 0) {
                throw new RegistryException(Code.INVALID_INPUT, "DER 인증서 뒤에 남는 바이트가 있습니다.");
            }
            return List.of(certificate);
        } catch (RegistryException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryException(Code.INVALID_INPUT, "X.509 인증서로 해석할 수 없습니다.", e);
        }
    }

    public static Inspected inspect(X509Certificate certificate, ServiceType type, TrustPointLevel level,
            Instant now) {
        try {
            byte[] der = certificate.getEncoded();
            byte[] extensionSki = extensionSki(certificate);
            byte[] ski = extensionSki != null ? extensionSki
                    : new JcaX509ExtensionUtils().createSubjectKeyIdentifier(certificate.getPublicKey())
                            .getKeyIdentifier();
            var record = new CertificateRecord(
                    Base64.getEncoder().encodeToString(der),
                    sha256Hex(der),
                    certificate.getSubjectX500Principal().getName(X500Principal.RFC2253),
                    certificate.getIssuerX500Principal().getName(X500Principal.RFC2253),
                    certificate.getSerialNumber().toString(16),
                    certificate.getNotBefore().toInstant(),
                    certificate.getNotAfter().toInstant(),
                    certificate.getPublicKey().getAlgorithm(),
                    sha256Hex(certificate.getPublicKey().getEncoded()),
                    HexFormat.of().formatHex(ski),
                    extensionSki != null,
                    now);
            return new Inspected(certificate, record, warnings(certificate, record, type, level, now));
        } catch (RegistryException e) {
            throw e;
        } catch (Exception e) {
            throw new RegistryException(Code.INVALID_INPUT, "인증서 값을 추출할 수 없습니다.", e);
        }
    }

    public static X509Certificate decode(CertificateRecord record) {
        return parse(Base64.getDecoder().decode(record.derBase64())).get(0);
    }

    private static List<String> warnings(X509Certificate certificate, CertificateRecord record, ServiceType type,
            TrustPointLevel level, Instant now) throws CertificateParsingException {
        var warnings = new ArrayList<String>();
        if (now.isAfter(record.notAfter())) warnings.add("유효기간이 만료된 인증서입니다.");
        if (now.isBefore(record.notBefore())) warnings.add("아직 유효기간이 시작되지 않은 인증서입니다.");
        if (!record.skiFromExtension()) {
            warnings.add("SubjectKeyIdentifier 확장이 없어 공개키 SHA-1로 SKI를 계산했습니다.");
        }
        if (type == ServiceType.CA && certificate.getBasicConstraints() < 0) {
            warnings.add("CA 서비스이지만 BasicConstraints cA=true가 아닙니다.");
        }
        if (type == ServiceType.OCSP && !hasExtendedKeyUsage(certificate, EKU_OCSP_SIGNING)) {
            warnings.add("OCSP 서비스이지만 OCSPSigning 확장키용도가 없습니다.");
        }
        if (type == ServiceType.TSA && !hasExtendedKeyUsage(certificate, EKU_TIME_STAMPING)) {
            warnings.add("TSA 서비스이지만 timeStamping 확장키용도가 없습니다.");
        }
        if (level == TrustPointLevel.ROOT
                && !certificate.getSubjectX500Principal().equals(certificate.getIssuerX500Principal())) {
            warnings.add("RootCA(레벨 0)로 등록하지만 자체 발급 인증서가 아닙니다.");
        }
        return warnings;
    }

    private static boolean hasExtendedKeyUsage(X509Certificate certificate, String oid)
            throws CertificateParsingException {
        var usages = certificate.getExtendedKeyUsage();
        return usages != null && usages.contains(oid);
    }

    private static byte[] extensionSki(X509Certificate certificate) throws Exception {
        byte[] value = certificate.getExtensionValue("2.5.29.14");
        if (value == null) return null;
        return SubjectKeyIdentifier.getInstance(JcaX509ExtensionUtils.parseExtensionValue(value))
                .getKeyIdentifier();
    }

    private static boolean isPem(byte[] input) {
        String head = new String(input, 0, Math.min(input.length, 256), StandardCharsets.US_ASCII);
        return head.stripLeading().startsWith("-----BEGIN");
    }

    static String sha256Hex(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
