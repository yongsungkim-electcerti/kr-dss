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
    // 특허-B: 인증서 발급 인프라(Registration Binding / Multi-RA / Lifecycle / HSM).
    implementation(project(":kr-dss-sdk:kr-dss-pki"))
    // 특허-C: 통합 신뢰목록(A/B/C 연계 — 검증 라우터에 신뢰목록 평가 주입).
    implementation(project(":kr-dss-sdk:kr-dss-trust"))
    implementation(libs.bc.prov)
    implementation(libs.bc.pkix)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// demo 프로파일의 TLS 키스토어 경로(./certs/demo-tls.p12)를 저장소 루트 기준으로 해석시킨다.
// JavaExec 기본 workingDir 은 모듈 디렉터리라, 그대로 두면 poc/poc-relying-party/certs 를 찾는다.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}

// 사업자 설명회 HTML 자료를 PoC 화면의 /presentation 경로에서 바로 제공한다.
tasks.processResources {
    from(rootProject.file("docs/사업자설명회")) {
        include("*.html")
        into("static/presentation")
    }
}
