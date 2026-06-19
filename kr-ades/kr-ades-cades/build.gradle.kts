plugins {
    id("krdss.java-conventions")
}

dependencies {
    api(project(":kr-ades:kr-ades-core"))
    implementation(libs.dss.cades)
    implementation(libs.dss.asic.cades)
}
