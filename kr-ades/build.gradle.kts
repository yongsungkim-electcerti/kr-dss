// [과업1] KR-AdES 배포 패키징 — Whale PoC(PDF Baseline) 반출용 산출물 구성.
//
//   output/libs      : 본 저장소에서 빌드한 KR-AdES 라이브러리(jar)
//   output/libs/ext  : 위 라이브러리가 런타임에 필요로 하는 외부 라이브러리
//                      (BouncyCastle 등 암호 라이브러리 · EU DSS · PDFBox …)
//
// 반출 프로파일 — 어떤 모듈을 담을지 결정한다. Whale PoC 는 PDF Baseline 이므로 pades 가 기본.
//
//   pades (기본)  PAdES 슬림 — kr-ades-pades + 전이 의존인 kr-ades-core  (2 + ext 23 jar)
//   full          6종 포맷 어댑터 전부(core/xades/cades/pades/jades/hades/mades) (7 + ext 47 jar)
//
// 실행:
//   ./gradlew :kr-ades:packageAdes                     # 기본 프로파일(pades)
//   ./gradlew :kr-ades:packageAdes -Pwhale.ades=full   # 전체
//
// `:kr-ades` 는 소스가 없는 집계 프로젝트다. jvm-ecosystem 플러그인은 태스크를 추가하지
// 않고 JVM 속성 스키마(Usage/LibraryElements/TargetJvmVersion 호환 규칙)만 등록하므로,
// 아래 커스텀 configuration 이 하위 모듈의 runtimeElements 를 정상적으로 해석할 수 있다.
plugins {
    id("jvm-ecosystem")
}

val outputLibs: Directory = rootProject.layout.projectDirectory.dir("output/libs")
val outputExt: Directory = outputLibs.dir("ext")

val defaultAdesProfile = "pades"
val activeAdesProfile = (findProperty("whale.ades") as String? ?: defaultAdesProfile).lowercase()

// 프로파일별 "진입 모듈". 전이 의존은 의존성 해석이 알아서 끌어온다.
// 예) pades 는 kr-ades-pades 만 선언해도 api(project(":kr-ades:kr-ades-core")) 를 통해
//     kr-ades-core 가 함께 담긴다.
val adesProfiles: Map<String, () -> List<Project>> = linkedMapOf(
    "pades" to { listOf(project(":kr-ades:kr-ades-pades")) },
    // 새 어댑터 모듈을 settings.gradle.kts 에 추가하면 full 프로파일에 자동 포함된다.
    "full" to { subprojects.toList() }
)

require(activeAdesProfile in adesProfiles) {
    "알 수 없는 반출 프로파일 '-Pwhale.ades=$activeAdesProfile'. 사용 가능: ${adesProfiles.keys.joinToString(", ")}"
}

// 진입 모듈들의 런타임 클래스패스를 한 번에 해석한다. 모듈별로 따로 복사하지 않고
// 통합 해석해야 버전 충돌 해소가 한 번만 적용되어 ext 에 같은 라이브러리의
// 서로 다른 버전이 섞이지 않는다.
//
// 프로파일마다 별도 태스크를 두면 두 태스크가 같은 output/libs 를 쓰게 되고, 그때 Gradle 의
// overlapping outputs 처리 때문에 "프로파일 전환 후 UP-TO-DATE" 로 스킵되어 이전 프로파일의
// jar 가 남는다. 그래서 태스크는 한 벌만 두고 활성 프로파일을 태스크 입력으로 넣는다.
val adesRuntime: Configuration by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    isVisible = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category::class.java, Category.LIBRARY))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling::class.java, Bundling.EXTERNAL))
        attribute(
            LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
            objects.named(LibraryElements::class.java, LibraryElements.JAR)
        )
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 21)
    }
}

dependencies {
    adesProfiles.getValue(activeAdesProfile)().forEach { add(adesRuntime.name, it) }
}

// 저장소에서 빌드한 산출물(= 프로젝트 컴포넌트)과 외부 의존(= 모듈 좌표)을 분리한다.
val builtJars: FileCollection = adesRuntime.incoming
    .artifactView { componentFilter { it is ProjectComponentIdentifier } }.files
val externalJars: FileCollection = adesRuntime.incoming
    .artifactView { componentFilter { it is ModuleComponentIdentifier } }.files

val packageAdesLibs by tasks.registering(Copy::class) {
    group = "whale"
    description = "KR-AdES 모듈 jar 를 output/libs 로 복사한다. 프로파일: -Pwhale.ades=pades|full"
    inputs.property("whale.ades", activeAdesProfile)
    // 프로파일 전환·버전 변경 시 이전 jar 가 남아 클래스패스가 오염되므로 매번 비우고 채운다.
    doFirst { delete(fileTree(outputLibs) { include("*.jar") }) }
    from(builtJars)
    into(outputLibs)
}

val packageAdesExt by tasks.registering(Copy::class) {
    group = "whale"
    description = "KR-AdES 가 참조하는 외부 라이브러리(암호·EU DSS 등)를 output/libs/ext 로 복사한다."
    inputs.property("whale.ades", activeAdesProfile)
    doFirst { delete(fileTree(outputExt) { include("*.jar") }) }
    from(externalJars)
    into(outputExt)
}

val packageAdes by tasks.registering {
    group = "whale"
    description = "활성 반출 프로파일(-Pwhale.ades, 기본 $defaultAdesProfile)로 output/libs 를 패키징한다."
    dependsOn(packageAdesLibs, packageAdesExt)
    doLast {
        val libCount = outputLibs.asFile.listFiles { f -> f.extension == "jar" }?.size ?: 0
        val extCount = outputExt.asFile.listFiles { f -> f.extension == "jar" }?.size ?: 0
        println("반출 프로파일    : $activeAdesProfile")
        println("output/libs      : $libCount jar")
        println("output/libs/ext  : $extCount jar")
    }
}

val cleanAdesOutput by tasks.registering(Delete::class) {
    group = "whale"
    description = "output/libs 산출물을 삭제한다."
    delete(outputLibs)
}
