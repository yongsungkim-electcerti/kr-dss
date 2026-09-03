// 루트 빌드 — 실제 구현은 모두 하위 모듈에 위치한다.
// 공통 규칙은 build-logic 의 컨벤션 플러그인(krdss.java-conventions)에서 관리한다.

tasks.register("printModules") {
    group = "help"
    description = "프로젝트에 포함된 모든 모듈 경로를 출력한다."
    doLast {
        subprojects.sortedBy { it.path }.forEach { println(it.path) }
    }
}

// --- Whale PoC(PDF Baseline) 산출물 패키징 ---
//
//   output/libs      KR-AdES 라이브러리          (:kr-ades:packageAdes)
//   output/libs/ext  참조 외부 라이브러리(암호·EU DSS 등)
//   output/poc       실행 모듈 rp · rssp          (:poc:packagePoc)
//
// 반출 프로파일은 -Pwhale.ades 로 고른다(기본 pades — PDF Baseline 슬림).
//   ./gradlew whalePackage                  # PAdES 슬림
//   ./gradlew whalePackage -Pwhale.ades=full  # 6종 포맷 전부
//
// 기동 스크립트(scripts/whale-startup.ps1)가 이 태스크를 호출한다.
tasks.register("whalePackage") {
    group = "whale"
    description = "Whale PoC 산출물(output/libs, output/poc)을 일괄 생성한다. 프로파일: -Pwhale.ades=pades|full"
    dependsOn(":kr-ades:packageAdes", ":poc:packagePoc")
}

tasks.register("whaleClean") {
    group = "whale"
    description = "Whale PoC 산출물(output/libs, output/poc)을 삭제한다. output/certs 는 보존한다."
    dependsOn(":kr-ades:cleanAdesOutput", ":poc:cleanPocOutput")
}
