plugins {
    id("krdss.java-conventions")
}

dependencies {
    api(project(":kr-ades:kr-ades-core"))
    // Markdown 은 Detached/ASiC 기반 — JSON 메타 + BouncyCastle.
    implementation(libs.jackson.databind)
    implementation(libs.bundles.bouncycastle)
}
