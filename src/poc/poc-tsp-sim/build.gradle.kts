plugins {
    id("krdss.java-conventions")
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    // 가상 인정사업자 CA/RA/OCSP — 특허-B 발급 인프라 + RFC 6960 OCSP 응답부 재사용
    implementation(project(":kr-dss-sdk:kr-dss-pki"))
    implementation(libs.bundles.bouncycastle)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.bundles.bouncycastle)
}
