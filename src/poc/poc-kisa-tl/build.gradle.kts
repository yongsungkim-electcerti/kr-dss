import java.util.Properties

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

// 실행 자료는 main에서 관리한다. Gradle 빌드 산출물은 워크트리별로 격리한다.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
    doFirst {
        val dataConfig = Properties().apply {
            rootProject.file("config/local-data.properties").inputStream().use { load(it) }
        }
        val dataRoot = file(System.getenv("KRTL_DATA_ROOT") ?: dataConfig.getProperty("mainRoot"))
        require(dataRoot.isAbsolute && dataRoot.isDirectory) { "자료 저장소가 없습니다: $dataRoot" }
        systemProperty("krtl.admin.storage-root", dataRoot.resolve(dataConfig.getProperty("runtime")).resolve("tl-management").absolutePath)
    }
}
