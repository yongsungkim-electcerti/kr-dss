// 루트 빌드 — 실제 구현은 모두 하위 모듈에 위치한다.
// 공통 규칙은 build-logic 의 컨벤션 플러그인(krdss.java-conventions)에서 관리한다.

tasks.register("printModules") {
    group = "help"
    description = "프로젝트에 포함된 모든 모듈 경로를 출력한다."
    doLast {
        subprojects.sortedBy { it.path }.forEach { println(it.path) }
    }
}
