plugins {
    id("krdss.java-conventions")
}

dependencies {
    // 공통 Core 데이터 모델은 DSS 모델/SPI 를 토대로 확장한다.
    api(libs.dss.model)
    api(libs.dss.spi)
    implementation(libs.slf4j.api)
}
