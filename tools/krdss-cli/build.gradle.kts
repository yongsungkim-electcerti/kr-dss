plugins {
    id("krdss.java-conventions")
    application
}

dependencies {
    implementation(project(":kr-dss-sdk:kr-dss-core"))
    implementation(libs.picocli)
    implementation(libs.logback.classic)
    annotationProcessor(libs.picocli) // picocli-codegen 대용(간이)
}

application {
    mainClass = "com.electcerti.krdss.cli.KrDssCli"
}
