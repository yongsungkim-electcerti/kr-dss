plugins {
    id("krdss.java-conventions")
}

dependencies {
    // 순수 인터페이스 + DTO 만 노출 — 외부 이용기관이 구현에 결합되지 않도록 한다.
    api(project(":kr-ades:kr-ades-core"))
    api(project(":kr-tl:kr-tl-model"))
}
