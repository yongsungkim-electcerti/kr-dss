plugins {
    id("krdss.java-conventions")
}

dependencies {
    // 특허-B 발급 인프라: 검증 경로 정책 OID(POLICY_WEBAUTHN 등) 재사용.
    implementation(project(":kr-ades:kr-ades-cades"))

    // X.509 인증서 발급 / CSR(PKCS#10) / ASN.1 확장 구성.
    implementation(libs.bc.prov)
    implementation(libs.bc.pkix)
}
