plugins {
    id("krdss.java-conventions")
}

dependencies {
    // 보안·크립토 계층: 해시·인증서 경로검증·OCSP/CRL·TSA.
    api(libs.bundles.bouncycastle)
    implementation(libs.dss.service)
    implementation(libs.dss.spi)
    implementation(libs.slf4j.api)
}
