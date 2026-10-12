plugins {
    id("krdss.java-conventions")
}

dependencies {
    api(project(":kr-tl:kr-tl-model"))
    // KR-TL 서명(JWS)은 payload 를 JSON 으로 직렬화한 바이트 위에 만든다.
    implementation(libs.jackson.databind)
    implementation(libs.jackson.jsr310)
    implementation(libs.bundles.bouncycastle)
    implementation(libs.slf4j.api)
    // XML TL 서명: ETSI TS 119 612의 enveloped XAdES (EU DSS)
    implementation(libs.dss.xades)
    implementation(libs.dss.utils.apache)
    // dss-xades가 optional로 두는 XML 스키마·서명 구조 처리(specs-xmldsig 등)
    implementation(libs.dss.validation)
}
