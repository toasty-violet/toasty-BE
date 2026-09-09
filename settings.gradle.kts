plugins {
    // org.gradle.java.home을 개인 경로로 커밋하는 대신, 어느 OS에서든 빌드 시
    // JDK 21 툴체인이 없으면 자동으로 다운로드하도록 한다.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "toasty"

// 단일 모듈로 시작한다. 도메인 경계가 분명해지면 domain/{도메인} 패키지를 그대로
// 모듈로 들어올리고 아래에 include를 추가한다. (CLAUDE.md "멀티모듈 전환" 참고)
