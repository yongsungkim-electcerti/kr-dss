plugins {
    id("krdss.java-conventions")
}

dependencies {
    api(project(":kr-ades:kr-ades-core"))
    implementation(libs.dss.xades)
    implementation(libs.dss.asic.xades)
}
