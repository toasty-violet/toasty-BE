plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.diffplug.spotless") version "8.9.0"
}

group = "com.toasty"
version = "0.0.1"
description = "toasty-server"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // web / api
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17")

    // persistence
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")

    // aws
    implementation(platform("software.amazon.awssdk:bom:2.46.7"))
    implementation("software.amazon.awssdk:ivs")

    // ops
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // @ConfigurationProperties 메타데이터 생성 (IDE 자동완성)
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    // 파라미터 이름 보존 — @RequestParam / @PathVariable에서 이름 생략 가능
    options.compilerArgs.add("-parameters")
    options.encoding = "UTF-8"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

spotless {
    java {
        // pre-commit 훅은 스테이징된 파일만 넘겨 검사 범위를 커밋 대상으로 좁힌다.
        // (관계없는 파일의 포맷 위반 때문에 커밋이 막히지 않고, 검사도 빠르다)
        // 훅 없이 직접 실행하면(./gradlew spotlessApply) 전체 소스를 대상으로 한다.
        val gitFilter = providers.gradleProperty("spotlessGitFilter").orNull
        if (gitFilter.isNullOrBlank()) {
            target("src/**/*.java")
        } else {
            target(gitFilter.split(Regex("\\s+")).filter { it.isNotBlank() })
        }

        // AOSP = google-java-format + 4-space. IntelliJ 기본값과 맞아 IDE 포맷터와 싸우지 않는다.
        googleJavaFormat("1.36.1").aosp().reflowLongStrings()
        removeUnusedImports()
        formatAnnotations()
        trimTrailingWhitespace()
        endWithNewline()
    }
}

// clone 후 별도 명령 없이 훅이 걸리도록 컴파일 시점에 hooksPath를 설정한다.
tasks.register<Exec>("installGitHooks") {
    // git 저장소가 아닌 환경(Docker 이미지 빌드 등)에서는 스킵한다.
    // configuration cache 호환을 위해 구성 시점에 평가한 값을 캡처한다.
    val isGitRepo = rootProject.file(".git").exists()
    onlyIf { isGitRepo }
    commandLine("git", "config", "core.hooksPath", ".githooks")
}

tasks.named("compileJava") {
    dependsOn(tasks.named("installGitHooks"))
}