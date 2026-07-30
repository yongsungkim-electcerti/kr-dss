plugins {
    id("krdss.java-conventions")
}

dependencies {
    // 공통 Core 데이터 모델은 DSS 모델/SPI 를 토대로 확장한다.
    api(libs.dss.model)
    api(libs.dss.spi)

    // DSS 의 IUtils 는 SPI 라 구현체가 런타임 클래스패스에 반드시 있어야 한다.
    // (없으면 서명·검증 첫 호출에서 "No implementation found for IUtils" 로 실패)
    // kr-ades-core 는 6종 어댑터와 kr-dss-core 가 모두 거치는 공통 기반이므로 여기서 한 번만 제공한다.
    runtimeOnly(libs.dss.utils.apache)

    implementation(libs.slf4j.api)
}
