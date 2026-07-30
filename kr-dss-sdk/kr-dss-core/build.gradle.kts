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
    // KrDssServiceImpl 이 EU DSS CAdES API 를 직접 사용하므로 직접 의존성이 필요하다.
    // kr-ades-cades 의 implementation 의존성은 소비 모듈에 노출되지 않는다.
    implementation(libs.dss.cades)

    // 6종 포맷 어댑터.
    runtimeOnly(project(":kr-ades:kr-ades-xades"))
    runtimeOnly(project(":kr-ades:kr-ades-pades"))
    runtimeOnly(project(":kr-ades:kr-ades-jades"))
    runtimeOnly(project(":kr-ades:kr-ades-hades"))
    runtimeOnly(project(":kr-ades:kr-ades-mades"))

    // 원격전자서명 서명객체 패키징(데모 KR-JAdES 컨테이너) 직렬화.
    implementation(libs.jackson.databind)

    // 특허-A: 결속 컨테이너 구성 + WebAuthn 어서션 COSE 실검증.
    implementation(project(":kr-ades:kr-ades-cades"))
    implementation(libs.webauthn4j.core)

    implementation(libs.slf4j.api)
}
