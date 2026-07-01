plugins {
    id("krdss.java-conventions")
}

dependencies {
    // 특허-C Layer1 신뢰서비스목록: KR-TL 모델(ETSI TS 119 612 / EU-TL 준용) 재사용.
    api(project(":kr-tl:kr-tl-model"))

    // 3분류 판정(ETSI EN 319 102-1) — 특허-A 라우터와 동일 enum.
    implementation(project(":kr-dss-sdk:kr-dss-api"))

    // 특허-B 장치 등급/레지스트리 인터페이스 — Layer2 서브목록이 이를 구현.
    implementation(project(":kr-dss-sdk:kr-dss-pki"))
}
