plugins {
    id("krdss.java-conventions")
}

// 원격전자서명 클라이언트·프로토콜 라이브러리.
// CSC v2 API 모델, 서명활성화데이터(SAD), 2단계 원격서명 흐름을 제공한다.
// PoC 서비스(RSSP/SAM/HSM)와 이용사(SIC)가 공통으로 의존한다.
dependencies {
    api(project(":kr-dss-sdk:kr-dss-api"))
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)
}
