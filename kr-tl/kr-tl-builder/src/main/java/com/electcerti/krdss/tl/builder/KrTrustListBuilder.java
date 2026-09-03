package com.electcerti.krdss.tl.builder;

import com.electcerti.krdss.tl.model.KrTrustList;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * KR-TL 생성 및 전자서명 책임.
 *
 * <p>운영 흐름: 인정사업자 제출 신뢰정보 → 수집·검토 → <b>KR-TL 생성·전자서명</b> → 배포.
 * 인정평가 결과를 TL 반영 기준 정보로 활용한다.</p>
 *
 * <h2>서명 형식 — JWS(RFC 7515) 평탄화 JSON 직렬화</h2>
 *
 * <p>ETSI TS 119 612 의 EU TL 은 XML + XAdES 이지만, 본 KR-TL 은 도메인 모델을 JSON 으로
 * 다루므로 JSON 서명 표준인 JWS 를 쓴다. JAdES(ETSI TS 119 182-1)도 JWS 위에 정의되어 있어
 * 이후 KR-JAdES 프로파일로 확장할 때 그대로 이어진다.</p>
 *
 * <p>JWS 를 택한 실질적인 이유는 <b>정규화 문제가 없다</b>는 점이다. 서명 대상이
 * "payload 를 base64url 로 인코딩한 문자열"로 고정되므로, 검증 측이 JSON 을 다시
 * 직렬화하면서 필드 순서·공백이 달라져 서명이 깨지는 일이 생기지 않는다.</p>
 *
 * <pre>
 * {
 *   "payload"  : base64url(KR-TL JSON),
 *   "protected": base64url({"alg":"RS256","typ":"kr-tl+jws","x5c":[...]}),
 *   "signature": base64url(RSA-SHA256( ASCII(protected + "." + payload) ))
 * }
 * </pre>
 */
public final class KrTrustListBuilder {

    /** 서명 대상 payload 직렬화에 쓰는 매퍼. 필드 순서를 고정해 산출물을 재현 가능하게 둔다. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static final Base64.Encoder URL = Base64.getUrlEncoder().withoutPadding();

    /** 수집·검토된 신뢰정보로부터 KR-TL 을 생성한다. */
    public KrTrustList build(KrTrustList draft) {
        return Objects.requireNonNull(draft, "draft");
    }

    /**
     * 생성된 KR-TL 에 전자서명을 부여해 무결성·진본성을 보장한다.
     *
     * @param trustList  서명 대상 신뢰목록
     * @param signingKey 신뢰목록 서명자 개인키
     * @param chain      서명자 인증서 체인 {@code [서명자, 발급 CA, …]} — JWS {@code x5c} 로 실린다
     * @return JWS(평탄화 JSON 직렬화) 바이트. 그대로 파일로 배포한다
     */
    public byte[] sign(KrTrustList trustList, PrivateKey signingKey, List<X509Certificate> chain) {
        Objects.requireNonNull(trustList, "trustList");
        Objects.requireNonNull(signingKey, "signingKey");
        if (chain == null || chain.isEmpty()) {
            throw new IllegalArgumentException("서명자 인증서 체인이 비어 있습니다.");
        }

        String algorithm = jwsAlgorithm(signingKey);
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", algorithm);
        header.put("typ", "kr-tl+jws");
        header.put("x5c", encodeChain(chain));

        String encodedPayload = URL.encodeToString(toJson(trustList));
        String encodedHeader = URL.encodeToString(toJson(header));
        // RFC 7515 §5.1 — 서명 입력은 ASCII(protected || '.' || payload) 다.
        byte[] signingInput = (encodedHeader + "." + encodedPayload).getBytes(StandardCharsets.US_ASCII);

        Map<String, Object> jws = new LinkedHashMap<>();
        jws.put("payload", encodedPayload);
        jws.put("protected", encodedHeader);
        jws.put("signature", URL.encodeToString(rawSign(algorithm, signingKey, signingInput)));
        return toJson(jws);
    }

    /** JWS {@code alg} 식별자. 서명 키 종류로 정한다. */
    static String jwsAlgorithm(PrivateKey key) {
        String algorithm = key.getAlgorithm();
        if ("RSA".equalsIgnoreCase(algorithm)) {
            return "RS256";
        }
        if ("EC".equalsIgnoreCase(algorithm) || "ECDSA".equalsIgnoreCase(algorithm)) {
            return "ES256";
        }
        throw new IllegalArgumentException("지원하지 않는 KR-TL 서명 키 알고리즘입니다: " + algorithm);
    }

    /** JWS {@code alg} → JCA 서명 알고리즘. */
    static String jcaAlgorithm(String jwsAlgorithm) {
        return switch (jwsAlgorithm) {
            case "RS256" -> "SHA256withRSA";
            case "RS384" -> "SHA384withRSA";
            case "RS512" -> "SHA512withRSA";
            case "ES256" -> "SHA256withECDSA";
            case "ES384" -> "SHA384withECDSA";
            case "ES512" -> "SHA512withECDSA";
            default -> throw new IllegalArgumentException("지원하지 않는 JWS alg 입니다: " + jwsAlgorithm);
        };
    }

    /** x5c 는 RFC 7515 §4.1.6 에 따라 base64url 이 아니라 표준 base64(DER)로 넣는다. */
    private static List<String> encodeChain(List<X509Certificate> chain) {
        List<String> encoded = new ArrayList<>(chain.size());
        for (X509Certificate certificate : chain) {
            try {
                encoded.add(Base64.getEncoder().encodeToString(certificate.getEncoded()));
            } catch (CertificateEncodingException e) {
                throw new IllegalStateException("서명자 인증서 인코딩 실패", e);
            }
        }
        return encoded;
    }

    private static byte[] rawSign(String jwsAlgorithm, PrivateKey key, byte[] input) {
        try {
            Signature signature = Signature.getInstance(jcaAlgorithm(jwsAlgorithm));
            signature.initSign(key);
            signature.update(input);
            return signature.sign();
        } catch (Exception e) {
            throw new IllegalStateException("KR-TL 서명값 생성 실패: " + e.getMessage(), e);
        }
    }

    private static byte[] toJson(Object value) {
        try {
            return MAPPER.writeValueAsBytes(value);
        } catch (Exception e) {
            throw new IllegalStateException("KR-TL 직렬화 실패: " + e.getMessage(), e);
        }
    }

    /** 서명·검증 양쪽이 같은 설정의 매퍼를 쓰도록 공유한다. */
    static ObjectMapper mapper() {
        return MAPPER;
    }
}
