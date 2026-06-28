plugins {
    id("krdss.java-conventions")
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    // 코어: 원격서명 오케스트레이션·KR-AdES 서명객체 패키징 + 검증 라우터(특허-A).
    implementation(project(":kr-dss-sdk:kr-dss-core"))
    // 원격서명 클라이언트(CSC v2)·SAD 모델 — SIC 가 RSSP 호출에 사용.
    implementation(project(":kr-dss-sdk:kr-dss-remote"))
    // 특허-A Mode 1: 서명 결속부·결속 컨테이너(kr-ades-cades) + CA 발급용 PKIX.
    implementation(project(":kr-ades:kr-ades-cades"))
    implementation(libs.bc.prov)
    implementation(libs.bc.pkix)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
