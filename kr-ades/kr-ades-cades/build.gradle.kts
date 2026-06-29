plugins {
    id("krdss.java-conventions")
}

dependencies {
    api(project(":kr-ades:kr-ades-core"))
    implementation(libs.dss.cades)
    implementation(libs.dss.asic.cades)

    // 특허-A: SignedAttrs/CMS 결속 컨테이너 구성용 ASN.1·PKIX
    implementation(libs.bc.prov)
    implementation(libs.bc.pkix)
}
