package com.electcerti.krdss.tl.builder;

import com.electcerti.krdss.tl.model.KrTrustList;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 배포된 KR-TL 파일을 읽고 그 전자서명을 검증한다.
 *
 * <p>{@link KrTrustListBuilder#sign} 이 만든 JWS 파일과, 서명이 없는 순수 KR-TL JSON 을
 * 모두 받는다. 어느 쪽인지는 {@link #signed()} 로 구분한다. 미서명 신뢰목록은 진본성을
 * 보장할 수 없으므로 호출부가 판정에 반영해야 한다.</p>
 *
 * @param trustList   신뢰목록 본문
 * @param signed      서명이 붙어 있는지
 * @param signerChain 서명자 인증서 체인 {@code [서명자, 발급 CA, …]} (미서명이면 빈 목록)
 * @param algorithm   JWS {@code alg} (미서명이면 null)
 * @param signatureValid 서명값이 본문과 서명자 공개키에 대해 유효한지
 * @param detail      판독·검증 결과 설명
 */
public record SignedKrTrustList(
        KrTrustList trustList,
        boolean signed,
        List<X509Certificate> signerChain,
        String algorithm,
        boolean signatureValid,
        String detail) {

    private static final Base64.Decoder URL = Base64.getUrlDecoder();

    /**
     * KR-TL 파일을 읽는다.
     *
     * <p>서명 검증까지 이 자리에서 끝낸다. 서명 대상 바이트({@code protected.payload})는
     * 파일에 실린 문자열 그대로여서, 본문을 다시 직렬화하지 않고 검증할 수 있다.</p>
     *
     * @throws IllegalArgumentException 파일이 KR-TL 도 서명된 KR-TL 도 아닐 때
     */
    public static SignedKrTrustList parse(byte[] document) {
        JsonNode root;
        try {
            root = KrTrustListBuilder.mapper().readTree(document);
        } catch (Exception e) {
            throw new IllegalArgumentException("KR-TL 파일을 JSON 으로 읽지 못했습니다: " + e.getMessage(), e);
        }

        boolean looksSigned = root.hasNonNull("payload") && root.hasNonNull("protected")
                && root.hasNonNull("signature");
        if (!looksSigned) {
            return new SignedKrTrustList(readTrustList(document), false, List.of(), null, false,
                    "전자서명이 없는 KR-TL 입니다. 진본성을 확인할 수 없습니다.");
        }

        String encodedPayload = root.get("payload").asText();
        String encodedHeader = root.get("protected").asText();
        String encodedSignature = root.get("signature").asText();

        byte[] payload;
        JsonNode header;
        try {
            payload = URL.decode(encodedPayload);
            header = KrTrustListBuilder.mapper().readTree(URL.decode(encodedHeader));
        } catch (Exception e) {
            throw new IllegalArgumentException("서명된 KR-TL 의 헤더·본문을 해독하지 못했습니다: " + e.getMessage(), e);
        }

        KrTrustList trustList = readTrustList(payload);
        String algorithm = header.path("alg").asText(null);
        List<X509Certificate> chain = decodeChain(header.path("x5c"));

        if (chain.isEmpty()) {
            return new SignedKrTrustList(trustList, true, chain, algorithm, false,
                    "서명 헤더에 서명자 인증서(x5c)가 없어 서명을 검증할 수 없습니다.");
        }
        if (algorithm == null) {
            return new SignedKrTrustList(trustList, true, chain, null, false,
                    "서명 헤더에 알고리즘(alg)이 없습니다.");
        }

        byte[] signingInput = (encodedHeader + "." + encodedPayload).getBytes(StandardCharsets.US_ASCII);
        boolean valid;
        String detail;
        try {
            valid = verify(algorithm, chain.get(0).getPublicKey(), signingInput, URL.decode(encodedSignature));
            detail = valid
                    ? "KR-TL 전자서명이 유효합니다."
                    : "KR-TL 전자서명이 본문과 일치하지 않습니다. 배포 후 변조되었을 수 있습니다.";
        } catch (Exception e) {
            valid = false;
            detail = "KR-TL 전자서명 검증에 실패했습니다: " + e.getMessage();
        }
        return new SignedKrTrustList(trustList, true, chain, algorithm, valid, detail);
    }

    /** 서명자(최종개체) 인증서. 미서명이면 null. */
    public X509Certificate signerCertificate() {
        return signerChain.isEmpty() ? null : signerChain.get(0);
    }

    private static boolean verify(String jwsAlgorithm, PublicKey publicKey, byte[] input, byte[] signatureValue)
            throws Exception {
        Signature signature = Signature.getInstance(KrTrustListBuilder.jcaAlgorithm(jwsAlgorithm));
        signature.initVerify(publicKey);
        signature.update(input);
        return signature.verify(signatureValue);
    }

    private static KrTrustList readTrustList(byte[] json) {
        try {
            return KrTrustListBuilder.mapper().readValue(json, KrTrustList.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("KR-TL 본문 파싱 실패: " + e.getMessage(), e);
        }
    }

    /** x5c 는 표준 base64(DER) 목록이다. */
    private static List<X509Certificate> decodeChain(JsonNode x5c) {
        if (x5c == null || !x5c.isArray()) {
            return List.of();
        }
        List<X509Certificate> chain = new ArrayList<>();
        try {
            CertificateFactory factory = CertificateFactory.getInstance("X.509");
            for (JsonNode entry : x5c) {
                byte[] der = Base64.getDecoder().decode(entry.asText());
                chain.add((X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der)));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("서명자 인증서(x5c) 해독 실패: " + e.getMessage(), e);
        }
        return List.copyOf(chain);
    }
}
