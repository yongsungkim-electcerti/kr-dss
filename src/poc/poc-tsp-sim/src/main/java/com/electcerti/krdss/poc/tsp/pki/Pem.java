package com.electcerti.krdss.poc.tsp.pki;

import java.security.Key;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;

/**
 * 데모용 PEM 인코딩 유틸(외부 의존 없이 표준 Base64/헤더로 구성).
 */
public final class Pem {

    private static final Base64.Encoder B64 =
            Base64.getMimeEncoder(64, new byte[] {'\n'});

    private Pem() {
    }

    /** X.509 인증서 → PEM. */
    public static String certificate(X509Certificate cert) {
        try {
            return wrap("CERTIFICATE", cert.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("인증서 PEM 인코딩 실패", e);
        }
    }

    /** 인증서 체인 → 연결된 PEM(발급 순서 유지). */
    public static String chain(List<X509Certificate> chain) {
        StringBuilder sb = new StringBuilder();
        for (X509Certificate c : chain) {
            sb.append(certificate(c));
        }
        return sb.toString();
    }

    /** 개인키(PKCS#8 DER) → PEM. */
    public static String privateKey(Key key) {
        return wrap("PRIVATE KEY", key.getEncoded());
    }

    private static String wrap(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + B64.encodeToString(der) + "\n"
                + "-----END " + type + "-----\n";
    }
}
