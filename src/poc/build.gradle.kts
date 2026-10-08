// PoC 배포 패키징 — Whale PoC(PDF Baseline) 실행 산출물 구성.
//
//   results/packages/whale/poc/rssp.jar   서버서명(RSSP/SSA)        :8090
//   results/packages/whale/poc/rp.jar     이용기관(Relying Party)    :8080
//   results/packages/whale/poc/config/    런타임 설정 오버라이드(Spring Boot 가 ./config 를 자동 인식)
//
// Whale PoC 범위: rp · rssp 2개 서비스만 기동한다.
//   - 원격전자서명 백엔드(poc-sam :8091 / poc-hsm :8092) 미사용
//   - TSA·OCSP 시뮬레이터(poc-tsp-sim :8082) 미사용 → PAdES BASELINE-B 범위
//   - KISA-TL 시뮬레이터(poc-kisa-tl :8081) 미사용
// 따라서 위 4개 모듈은 패키징 대상에서 제외한다(빌드 자체는 그대로 유지).
//
// 실행:  ./gradlew :poc:packagePoc

val outputPoc: Directory = rootProject.layout.projectDirectory.dir("results/packages/whale/poc")

// alias -> 모듈 경로. alias 가 그대로 results/packages/whale/poc 의 jar 이름·기동 스크립트 서비스명이 된다.
val whaleServices = linkedMapOf(
    "rssp" to ":poc:poc-rssp",
    "rp" to ":poc:poc-relying-party"
)

// bootJar 는 Spring Boot 플러그인이 각 하위 모듈에 등록하므로, 태스크를 참조하기 전에
// 해당 프로젝트가 평가되어 있어야 한다.
whaleServices.values.forEach { evaluationDependsOn(it) }

val packagePoc by tasks.registering(Copy::class) {
    group = "whale"
    description = "Whale PoC 실행 모듈(rp · rssp)의 실행 가능 jar 를 results/packages/whale/poc 로 패키징한다."
    doFirst { delete(fileTree(outputPoc) { include("*.jar") }) }
    whaleServices.forEach { (alias, path) ->
        // bootJar 의 출력은 실행 가능한 fat jar 하나뿐이다. from(TaskProvider) 로 넣으면
        // 태스크 의존성도 함께 걸린다.
        from(project(path).tasks.named("bootJar")) {
            rename { "$alias.jar" }
        }
    }
    into(outputPoc)
    doLast {
        outputPoc.dir("config").asFile.mkdirs()
        whaleServices.keys.forEach { println("results/packages/whale/poc/$it.jar") }
    }
}

val cleanPocOutput by tasks.registering(Delete::class) {
    group = "whale"
    description = "results/packages/whale/poc 산출물을 삭제한다."
    delete(outputPoc)
}
