plugins {
    id("krdss.java-conventions")
}

dependencies {
    // TS 119 612 신뢰목록 모델은 DSS 모델을 토대로 한국형 확장 필드를 추가한다.
    api(libs.dss.model)
}
