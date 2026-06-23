plugins {
    id("krdss.java-conventions")
}

dependencies {
    api(project(":kr-ades:kr-ades-core"))
    api(libs.dss.cades)
    api(libs.dss.asic.cades)
    implementation(libs.dss.utils.apache)
}
