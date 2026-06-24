plugins {
    id("krdss.java-conventions")
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
}

// RSSP/SSA — 원격서명 서비스 제공자(서버서명 애플리케이션).
// 이용사(SIC)에게 CSC v2 API 를 제공하고, SAM·HSM 을 오케스트레이션한다.
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(project(":kr-dss-sdk:kr-dss-remote"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
