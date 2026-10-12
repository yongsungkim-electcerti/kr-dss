plugins {
    id("krdss.java-conventions")
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(project(":kr-tl:kr-tl-builder"))
    implementation(project(":kr-tl:kr-tl-client"))
    // 인증서 SKI 추출·계산
    implementation(libs.bc.pkix)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

// 관리 저장소(runtime/tl-management)를 저장소 루트 기준으로 해석시킨다.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}
