plugins {
    id("krdss.java-conventions")
    id("org.springframework.boot") version "3.3.2"
    id("io.spring.dependency-management") version "1.1.6"
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(libs.bundles.bouncycastle)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
