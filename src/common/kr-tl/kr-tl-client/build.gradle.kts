plugins {
    id("krdss.java-conventions")
}

dependencies {
    api(project(":kr-tl:kr-tl-model"))
    // 수집·동기화·검증은 DSS TSL 검증 기능을 재사용한다.
    implementation(libs.dss.tsl.validation)
    implementation(libs.slf4j.api)
}
