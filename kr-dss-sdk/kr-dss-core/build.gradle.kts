plugins {
    id("krdss.java-conventions")
}

dependencies {
    // 핵심 서비스: 생성·추출·검증 전 구간 오케스트레이션.
    api(project(":kr-dss-sdk:kr-dss-api"))
    implementation(project(":kr-dss-sdk:kr-dss-crypto"))
    implementation(project(":kr-dss-sdk:kr-dss-report"))
    implementation(project(":kr-tl:kr-tl-client"))

    // 6종 포맷 어댑터.
    runtimeOnly(project(":kr-ades:kr-ades-xades"))
    runtimeOnly(project(":kr-ades:kr-ades-cades"))
    runtimeOnly(project(":kr-ades:kr-ades-pades"))
    runtimeOnly(project(":kr-ades:kr-ades-jades"))
    runtimeOnly(project(":kr-ades:kr-ades-hades"))
    runtimeOnly(project(":kr-ades:kr-ades-mades"))

    // 원격전자서명 서명객체 패키징(데모 KR-JAdES 컨테이너) 직렬화.
    implementation(libs.jackson.databind)

    implementation(libs.slf4j.api)
}
