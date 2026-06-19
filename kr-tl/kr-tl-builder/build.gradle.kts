plugins {
    id("krdss.java-conventions")
}

dependencies {
    api(project(":kr-tl:kr-tl-model"))
    implementation(libs.bundles.bouncycastle)
    implementation(libs.slf4j.api)
}
