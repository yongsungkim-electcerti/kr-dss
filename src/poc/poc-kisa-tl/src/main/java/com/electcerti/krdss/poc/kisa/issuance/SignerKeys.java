package com.electcerti.krdss.poc.kisa.issuance;

import com.electcerti.krdss.poc.kisa.registry.RegistryException;
import com.electcerti.krdss.poc.kisa.registry.RegistryException.Code;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * TL 서명 키 파일(설계 27의 T-02 노드: key.pem·chain.pem). 발행 때마다 읽는다.
 *
 * @param keyInfoChain KeyInfo에 싣는 체인(서명자 → TL CA). 신뢰앵커 Root는 이용기관이 따로 가진다.
 */
public record SignerKeys(PrivateKey key, X509Certificate certificate, List<X509Certificate> keyInfoChain) {

    public static SignerKeys load(Path dir) {
        try {
            var chain = new ArrayList<X509Certificate>();
            for (var cert : CertificateFactory.getInstance("X.509")
                    .generateCertificates(new ByteArrayInputStream(Files.readAllBytes(dir.resolve("chain.pem"))))) {
                chain.add((X509Certificate) cert);
            }
            if (chain.isEmpty()) throw new IllegalStateException("chain.pem에 인증서가 없습니다.");
            var certificate = chain.get(0);
            String pem = Files.readString(dir.resolve("key.pem"));
            if (!pem.contains("-----BEGIN PRIVATE KEY-----")) {
                throw new IllegalStateException("key.pem은 암호화되지 않은 PKCS#8 PEM이어야 합니다.");
            }
            String body = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
            var key = KeyFactory.getInstance(certificate.getPublicKey().getAlgorithm())
                    .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(body)));
            if (!matches(key, certificate)) throw new IllegalStateException("key.pem과 서명 인증서의 키가 다릅니다.");
            var keyInfo = chain.size() > 1 ? List.copyOf(chain.subList(0, chain.size() - 1)) : List.of(certificate);
            return new SignerKeys(key, certificate, keyInfo);
        } catch (Exception e) {
            throw new RegistryException(Code.KEY_READ_FAILED, "TL 서명 키를 읽지 못했습니다(" + dir + "): " + e.getMessage(), e);
        }
    }

    /** 서명 인증서 DER의 SHA-256 지문. */
    public String certificateSha256() {
        try {
            return IssuanceService.sha256(certificate.getEncoded());
        } catch (CertificateEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean matches(PrivateKey key, X509Certificate certificate) throws Exception {
        String alg = key.getAlgorithm().startsWith("EC") ? "SHA256withECDSA" : "SHA256withRSA";
        byte[] probe = new byte[32];
        new SecureRandom().nextBytes(probe);
        var signer = Signature.getInstance(alg);
        signer.initSign(key);
        signer.update(probe);
        var verifier = Signature.getInstance(alg);
        verifier.initVerify(certificate.getPublicKey());
        verifier.update(probe);
        return verifier.verify(signer.sign());
    }
}
