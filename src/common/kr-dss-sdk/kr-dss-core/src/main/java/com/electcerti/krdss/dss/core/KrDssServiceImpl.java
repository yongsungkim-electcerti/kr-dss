package com.electcerti.krdss.dss.core;

import com.electcerti.krdss.ades.cades.CAdesProfileAdapter;
import com.electcerti.krdss.ades.core.KrAdesFormat;
import com.electcerti.krdss.ades.core.PackagingType;
import com.electcerti.krdss.dss.api.Dtos.ExtractRequest;
import com.electcerti.krdss.dss.api.Dtos.ExtractResult;
import com.electcerti.krdss.dss.api.Dtos.SignRequest;
import com.electcerti.krdss.dss.api.Dtos.SignResult;
import com.electcerti.krdss.dss.api.Dtos.TimestampRequest;
import com.electcerti.krdss.dss.api.Dtos.TimestampResult;
import com.electcerti.krdss.dss.api.Dtos.VerifyRequest;
import com.electcerti.krdss.dss.api.Dtos.VerifyResult;
import com.electcerti.krdss.dss.api.KrDssService;
import com.electcerti.krdss.dss.api.VerificationStatus;
import com.electcerti.krdss.tl.model.KrTrustList;
import eu.europa.esig.dss.cades.CAdESSignatureParameters;
import eu.europa.esig.dss.cades.signature.CAdESService;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;
import java.util.Objects;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.util.Store;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;

/**
 * {@link KrDssService} 기본 구현 — 전 과정 오케스트레이션의 골격.
 *
 * <p>생성(A) 흐름: 서명 생성 → 패키징·문서 주입.<br>
 * 검증(B) 흐름: 추출 → KR-TL 조회 → 인증서·정책 검증 → 무결성 검증 → 판정·보고서.</p>
 */
public class KrDssServiceImpl implements KrDssService {

    private static final SignatureAlgorithm SIGNATURE_ALGORITHM = SignatureAlgorithm.RSA_SHA256;

    private final CAdesProfileAdapter cadesAdapter;
    private final CAdESService cadesService;
    private final SigningMaterial signingMaterial;

    /** 테스트 가능한 기본 CAdES 구현을 생성한다. */
    public KrDssServiceImpl() {
        this(new CAdesProfileAdapter(), createSigningMaterial());
    }

    KrDssServiceImpl(CAdesProfileAdapter cadesAdapter, SigningMaterial signingMaterial) {
        this.cadesAdapter = Objects.requireNonNull(cadesAdapter, "cadesAdapter");
        this.signingMaterial = Objects.requireNonNull(signingMaterial, "signingMaterial");
        this.cadesService = new CAdESService(new CommonCertificateVerifier());
    }

    @Override
    public SignResult sign(SignRequest request) {
        Objects.requireNonNull(request, "request");
        requireCades(request.format());

        if (request.packaging() == PackagingType.ASIC) {
            throw new UnsupportedOperationException("ASiC-with-CAdES signing is not yet wired into kr-dss-core");
        }

        byte[] documentBytes = Objects.requireNonNull(request.document(), "document");
        CAdESSignatureParameters parameters = cadesAdapter.newSignatureParameters(request.level(), request.packaging());
        parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
        parameters.setSigningCertificate(signingMaterial.certificateToken());
        parameters.setCertificateChain(signingMaterial.certificateToken());

        DSSDocument document = new InMemoryDocument(documentBytes, "kr-dss-message.txt");
        ToBeSigned dataToSign = cadesService.getDataToSign(document, parameters);
        SignatureValue signatureValue = new SignatureValue(SIGNATURE_ALGORITHM, sign(dataToSign.getBytes()));
        DSSDocument signedDocument = cadesService.signDocument(document, parameters, signatureValue);

        return new SignResult(toByteArray(signedDocument), KrAdesFormat.CADES, request.level());
    }

    @Override
    public ExtractResult extract(ExtractRequest request) {
        throw new UnsupportedOperationException("extract() not yet implemented");
    }

    @Override
    @SuppressWarnings("unchecked")
    public VerifyResult verify(VerifyRequest request) {
        Objects.requireNonNull(request, "request");
        requireCades(request.format());

        try {
            CMSSignedData signedData = new CMSSignedData(Objects.requireNonNull(request.signedDocument(), "signedDocument"));
            SignerInformationStore signerInfos = signedData.getSignerInfos();
            Store<X509CertificateHolder> certificates = signedData.getCertificates();

            if (signedData.getSignedContent() == null || signerInfos.size() == 0) {
                return verificationResult(VerificationStatus.TOTAL_FAILED, "CAdES signed content or signer is missing");
            }

            for (SignerInformation signer : signerInfos.getSigners()) {
                Collection<X509CertificateHolder> matches = certificates.getMatches(signer.getSID());
                if (matches.isEmpty()) {
                    return verificationResult(VerificationStatus.TOTAL_FAILED, "Signing certificate is missing");
                }
                X509CertificateHolder certificate = matches.iterator().next();
                boolean valid = signer.verify(new JcaSimpleSignerInfoVerifierBuilder()
                        .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                        .build(certificate));
                if (!valid) {
                    return verificationResult(VerificationStatus.TOTAL_FAILED, "CAdES signature value is invalid");
                }
            }

            return verificationResult(VerificationStatus.TOTAL_PASSED, "CAdES signature verification passed");
        } catch (Exception e) {
            return verificationResult(VerificationStatus.TOTAL_FAILED, "CAdES signature verification failed: " + e.getMessage());
        }
    }

    @Override
    public KrTrustList getTrustList() {
        throw new UnsupportedOperationException("getTrustList() not yet implemented");
    }

    @Override
    public KrTrustList refreshTrustList() {
        throw new UnsupportedOperationException("refreshTrustList() not yet implemented");
    }

    @Override
    public TimestampResult timestamp(TimestampRequest request) {
        throw new UnsupportedOperationException("timestamp() not yet implemented");
    }

    private void requireCades(KrAdesFormat format) {
        if (format != KrAdesFormat.CADES) {
            throw new UnsupportedOperationException("Only CAdES is implemented in kr-dss-core: " + format);
        }
    }

    private byte[] sign(byte[] dataToSign) {
        try {
            Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM.getJCEId(), BouncyCastleProvider.PROVIDER_NAME);
            signature.initSign(signingMaterial.privateKey());
            signature.update(dataToSign);
            return signature.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign CAdES data", e);
        }
    }

    private static byte[] toByteArray(DSSDocument document) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.writeTo(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read DSS document", e);
        }
    }

    private static VerifyResult verificationResult(VerificationStatus status, String message) {
        return new VerifyResult(status, message.getBytes(StandardCharsets.UTF_8));
    }

    private static SigningMaterial createSigningMaterial() {
        try {
            ensureBouncyCastleProvider();

            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            X509Certificate certificate = createSelfSignedCertificate(keyPair);
            return new SigningMaterial(keyPair.getPrivate(), new CertificateToken(certificate));
        } catch (GeneralSecurityException | OperatorCreationException | CertIOException e) {
            throw new IllegalStateException("Failed to create KR-DSS test signing material", e);
        }
    }

    private static X509Certificate createSelfSignedCertificate(KeyPair keyPair)
            throws OperatorCreationException, CertificateException, CertIOException {
        X500Name subject = new X500Name("CN=KR-DSS CAdES Test Signer");
        Instant now = Instant.now();
        Date notBefore = Date.from(now.minus(1, ChronoUnit.DAYS));
        Date notAfter = Date.from(now.plus(365, ChronoUnit.DAYS));

        JcaX509v3CertificateBuilder certificateBuilder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now.toEpochMilli()),
                notBefore,
                notAfter,
                subject,
                keyPair.getPublic());
        certificateBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        certificateBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));

        ContentSigner contentSigner = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM.getJCEId())
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPair.getPrivate());
        X509CertificateHolder holder = certificateBuilder.build(contentSigner);
        return new JcaX509CertificateConverter()
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .getCertificate(holder);
    }

    private static void ensureBouncyCastleProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    record SigningMaterial(PrivateKey privateKey, CertificateToken certificateToken) {
    }
}
