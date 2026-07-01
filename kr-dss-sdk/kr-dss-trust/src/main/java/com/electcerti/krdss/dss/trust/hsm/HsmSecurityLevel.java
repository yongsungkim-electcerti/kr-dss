package com.electcerti.krdss.dss.trust.hsm;

/**
 * HSM 보안 수준 (특허-C 청구항 9 — {@code securityLevel}).
 *
 * @param ccLevel     CC EAL 등급(예: EAL4+; 없으면 빈 문자열)
 * @param fipsLevel   FIPS 등급(예: FIPS 140-3 L3; 없으면 빈 문자열)
 * @param vendorLevel 제조사 자체 등급(없으면 빈 문자열)
 */
public record HsmSecurityLevel(String ccLevel, String fipsLevel, String vendorLevel) {

    public HsmSecurityLevel {
        ccLevel = ccLevel == null ? "" : ccLevel;
        fipsLevel = fipsLevel == null ? "" : fipsLevel;
        vendorLevel = vendorLevel == null ? "" : vendorLevel;
    }

    public static HsmSecurityLevel of(String ccLevel, String fipsLevel) {
        return new HsmSecurityLevel(ccLevel, fipsLevel, "");
    }
}
