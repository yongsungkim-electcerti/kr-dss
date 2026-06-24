plugins {
    id("krdss.java-conventions")
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
}

// SAM — 서명활성화모듈. 서명자의 단독 통제권(SAD)을 검증하고,
// 검증을 통과한 경우에만 HSM 에 서명연산을 지시한다. EN 419 241 의 핵심 구성요소.
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(project(":kr-dss-sdk:kr-dss-remote"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
