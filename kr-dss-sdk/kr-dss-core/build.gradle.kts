plugins {
    id("krdss.java-conventions")
}

dependencies {
    // 핵심 서비스: 생성·추출·검증 전 구간 오케스트레이션.
    api(project(":kr-dss-sdk:kr-dss-api"))
    implementation(project(":kr-dss-sdk:kr-dss-crypto"))
    implementation(project(":kr-dss-sdk:kr-dss-report"))
    implementation(project(":kr-tl:kr-tl-client"))
    implementation(project(":kr-ades:kr-ades-cades"))

    // 6종 포맷 어댑터.
    runtimeOnly(project(":kr-ades:kr-ades-xades"))
    runtimeOnly(project(":kr-ades:kr-ades-pades"))
    runtimeOnly(project(":kr-ades:kr-ades-jades"))
    runtimeOnly(project(":kr-ades:kr-ades-hades"))
    runtimeOnly(project(":kr-ades:kr-ades-mades"))

    implementation(libs.slf4j.api)
}
