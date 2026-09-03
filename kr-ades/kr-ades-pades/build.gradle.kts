plugins {
    id("krdss.java-conventions")
}

dependencies {
    api(project(":kr-ades:kr-ades-core"))
    implementation(libs.dss.pades)
    // dss-pades-pdfbox 가 끌어오는 버전과 반드시 같아야 한다(카탈로그의 pdfbox 주석 참고).
    implementation(libs.pdfbox)
}
