plugins {
    id("krdss.java-conventions")
}

dependencies {
    api(project(":kr-ades:kr-ades-core"))
    // HWP 는 DSS 미지원 — hwplib + BouncyCastle 로 자체 구현한다.
    implementation(libs.hwplib)
    implementation(libs.bundles.bouncycastle)
}
