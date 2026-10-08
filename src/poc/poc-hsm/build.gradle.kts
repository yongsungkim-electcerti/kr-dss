plugins {
    id("krdss.java-conventions")
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
}

// HSM — 소프트HSM(실증용). 서명키를 보관하고 다이제스트에 대한 서명연산을 수행한다.
// 실제 운영의 HSM/QSCD 경계를 모사하며, SAM 의 활성화 지시에만 서명키를 사용한다.
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(libs.bundles.bouncycastle)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
