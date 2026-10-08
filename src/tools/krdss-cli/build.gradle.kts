plugins {
    id("krdss.java-conventions")
    application
}

dependencies {
    implementation(project(":kr-dss-sdk:kr-dss-core"))
    implementation(libs.picocli)
    implementation(libs.bundles.bouncycastle) // 인증서 발급(cert gen)용 PKI 라이브러리
    implementation(libs.jackson.databind) // 체인 발급 템플릿(JSON) 파싱
    implementation(libs.logback.classic)
    annotationProcessor(libs.picocli) // picocli-codegen 대용(간이)
}

application {
    mainClass = "com.electcerti.krdss.cli.KrDssCli"
}
