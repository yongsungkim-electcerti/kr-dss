package com.electcerti.krdss.ades.cades.bind;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 암호 민첩성(Crypto Agility) — 결속 challenge 파생에 사용할 해시 알고리즘 모음.
 *
 * <p>특허-A 청구항 4/8: SignedAttrs 인코딩값의 해시로 challenge 를 생성하되, 사용
 * 해시 알고리즘을 정책에 따라 교체할 수 있어야 한다. 각 항목은 JCA 알고리즘명과
 * NIST/표준 OID 를 보유하며, 사용된 알고리즘 식별자는 전자서명 객체에 기재된다.</p>
 *
 * <p>SHA-256/384/512 및 SHA3-256 은 JDK 표준(Java 9+)으로 동작한다. SM3 는 국내
 * 정책 확정 시 추가 예정으로, 현재는 식별자만 예약한다.</p>
 */
public enum HashSuite {

    SHA_256("SHA-256", "2.16.840.1.101.3.4.2.1", true),
    SHA_384("SHA-384", "2.16.840.1.101.3.4.2.2", true),
    SHA_512("SHA-512", "2.16.840.1.101.3.4.2.3", true),
    SHA3_256("SHA3-256", "2.16.840.1.101.3.4.2.8", true),
    /** SM3 — 식별자만 예약(미구현). */
    SM3("SM3", "1.2.156.10197.1.401", false);

    private final String jcaName;
    private final String oid;
    private final boolean supported;

    HashSuite(String jcaName, String oid, boolean supported) {
        this.jcaName = jcaName;
        this.oid = oid;
        this.supported = supported;
    }

    /** JCA 알고리즘명(예: {@code SHA-256}). */
    public String jcaName() {
        return jcaName;
    }

    /** 해시 알고리즘 OID. */
    public String oid() {
        return oid;
    }

    /** 현재 PoC 에서 구현·지원되는 알고리즘인지 여부. */
    public boolean supported() {
        return supported;
    }

    /** 이 알고리즘의 {@link MessageDigest} 인스턴스를 생성한다. */
    public MessageDigest newDigest() {
        if (!supported) {
            throw new UnsupportedOperationException("미지원 해시 알고리즘(식별자 예약): " + name());
        }
        try {
            return MessageDigest.getInstance(jcaName);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("해시 알고리즘 사용 불가: " + jcaName, e);
        }
    }

    /** 입력 바이트의 해시를 계산한다. */
    public byte[] digest(byte[] input) {
        return newDigest().digest(input);
    }

    /** OID 로 알고리즘을 조회한다. */
    public static HashSuite fromOid(String oid) {
        for (HashSuite s : values()) {
            if (s.oid.equals(oid)) {
                return s;
            }
        }
        throw new IllegalArgumentException("알 수 없는 해시 OID: " + oid);
    }
}
