pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    // JDK 21 toolchain 자동 프로비저닝(로컬에 JDK 21 이 없을 때 자동 다운로드)
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "kr-dss"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// [과업 1] KR-AdES — 전자서명 규격 표준화
include("kr-ades:kr-ades-core")
include("kr-ades:kr-ades-xades")
include("kr-ades:kr-ades-cades")
include("kr-ades:kr-ades-pades")
include("kr-ades:kr-ades-jades")
include("kr-ades:kr-ades-hades")
include("kr-ades:kr-ades-mades")

// [과업 2] KR-TL — 신뢰검증 체계
include("kr-tl:kr-tl-model")
include("kr-tl:kr-tl-builder")
include("kr-tl:kr-tl-client")

// [과업 3] KR-DSS SDK — 전 과정 통합 지원
include("kr-dss-sdk:kr-dss-api")
include("kr-dss-sdk:kr-dss-crypto")
include("kr-dss-sdk:kr-dss-core")
include("kr-dss-sdk:kr-dss-report")

// PoC — 전 과정 실증 테스트베드
include("poc:poc-tsp-sim")
include("poc:poc-kisa-tl")
include("poc:poc-relying-party")

// Tools
include("tools:krdss-cli")
