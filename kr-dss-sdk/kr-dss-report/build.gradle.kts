plugins {
    id("krdss.java-conventions")
}

dependencies {
    api(project(":kr-dss-sdk:kr-dss-api"))
    // ETSI Validation Report 생성은 DSS validation 모듈을 재사용한다.
    implementation(libs.dss.validation)
}
