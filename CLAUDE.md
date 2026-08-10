# CLAUDE.md

Claude Code(claude.ai/code)가 이 저장소에서 작업할 때의 지침이다. 사람이 읽어도 되는 컨벤션 문서를 겸한다.

## 작업 원칙

**구현 전에 확실하지 않은 것은 반드시 물어본다.**

- 요구사항이 여러 방식으로 해석될 수 있으면 임의로 고르지 말고 선택지를 제시한다.
- 이 문서에 없는 패턴을 새로 도입해야 할 때, 어느 패키지에 둘지 애매할 때도 먼저 확인한다.
- 요청받은 범위만 수정한다. 인접 코드 개선·리팩토링은 임의로 하지 않는다.
- 테스트 코드는 명시적으로 요청받지 않으면 작성하지 않는다.

## 프로젝트 개요

Java 21 + Spring Boot 3.5 단일 모듈 서버. 패키지 루트는 `com.toasty`. DB는 MySQL 8, 스키마는 Flyway로만 변경한다.

프론트엔드는 별도 저장소([toasty-FE](https://github.com/toasty-violet/toasty-FE), Next.js)이며 개발 서버는 3000번 포트를 쓴다.

## 명령어

```bash
./gradlew build                  # 전체 빌드 (spotlessCheck 포함)
./gradlew bootRun                # 애플리케이션 실행
./gradlew test                   # 전체 테스트
./gradlew test --tests "com.toasty.domain.user.UserServiceTest"   # 단일 테스트
./gradlew spotlessApply          # 포맷 자동 수정 (pre-commit 훅이 자동 실행함)
./gradlew spotlessCheck          # 포맷 검사만

docker compose -f docker/local/docker-compose.yml up -d    # 로컬 MySQL 기동
docker compose -f docker/local/docker-compose.yml down      # 중지
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

## 패키지 구조

```
src/main/java/com/toasty/
├── ToastyApplication.java
├── global/                     # 도메인에 속하지 않는 공통 인프라
│   ├── config/                 #   WebConfig, CorsProperties, SwaggerConfig, JpaAuditingConfig
│   ├── entity/                 #   BaseTimeEntity
│   ├── exception/              #   ErrorCode, CommonErrorCode, CustomException,
│   │                           #   ErrorResponse, GlobalExceptionHandler
│   └── response/               #   ApiResponse
└── domain/
    └── {도메인}/                # 도메인 하나 = 디렉토리 하나 (예: user, post)
        ├── controller/         #   {도메인}Controller
        │   └── dto/
        │       ├── request/    #     {도메인}{동작}Request
        │       └── response/   #     {도메인}{동작}Response
        ├── service/            #   {도메인}Service
        ├── entity/             #   {도메인} (JPA Entity), VO, {도메인}{동작}Command
        ├── repository/         #   {도메인}Repository
        └── exception/          #   {도메인}ErrorCode
```

**도메인 간 참조**: 다른 도메인의 `repository`를 직접 참조하지 않는다. 필요하면 상대 도메인의 `Service`를 통해서만 접근한다. 이 규칙을 지켜야 나중에 도메인을 모듈로 떼어낼 수 있다.

## 레이어 규칙

```
Controller  (Presentation)  — HTTP 입출력, 검증, DTO ↔ Command 변환
    ↓
Service     (Business)      — 유스케이스 조립, 트랜잭션 경계
    ↓
Repository  (Data Access)   — 쿼리
```

1. **위에서 아래로만 참조한다.** 역방향 참조 금지 (Repository가 Service를 참조하지 않는다).
2. **같은 레이어끼리 참조하지 않는다.** Service가 다른 Service를 부르는 것은 도메인 간 참조일 때만 허용한다(위 "도메인 간 참조" 참고).
3. Service는 Repository를 직접 참조한다. 중간 레이어를 강제하지 않는다.

`@Transactional`은 Service에 붙인다. 조회만 하는 메서드는 `@Transactional(readOnly = true)`.

### 도메인이 커졌을 때 (선택)

Service 하나가 너무 커지거나 같은 조회·검증 로직이 여러 Service에 중복되면, **그 도메인에만** `implement/` 를 두고 재사용 단위를 뽑아낸다.

```
Service  →  implement/  →  Repository       # implement를 둔 도메인
Service  →  Repository                       # 나머지 도메인 (기본)
```

- 전 도메인에 일괄 적용하지 않는다. 필요한 도메인만 둔다.
- 네이밍(`Reader`/`Appender`/`Validator`/`Manager` 등)은 도메인 특성에 맞춰 정한다.
- `implement`를 둔 도메인에서는 Service가 Repository를 건너뛰고 `implement`를 통한다. 두 경로를 섞지 않는다.
- `implement` 내부끼리는 참조해도 된다.
- 다른 도메인의 `implement`는 참조하지 않는다. 도메인 간 접근은 `Service` 경유 원칙이 그대로 적용된다.

처음부터 넣지 말고, 실제로 중복이 생긴 뒤에 뽑는다.

## 자주 틀리는 규칙

- **Request DTO를 Service로 그대로 넘기지 않는다.** Controller에서 `request.toCommand()`로 `{도메인}{동작}Command`로 변환해 전달한다. Service가 웹 계층 타입을 모르게 한다.
- **응답은 항상 `ApiResponse`로 감싼다.** 성공은 `ApiResponse.ok(data)` / `ApiResponse.ok()`. 실패 응답은 `GlobalExceptionHandler`에서만 만든다 (`ApiResponse.fail(...)`).
- **Controller에서 try-catch로 응답을 만들지 않는다.** `throw new CustomException(errorCode)`만 하고 변환은 `GlobalExceptionHandler`에 맡긴다.
- **예외 클래스를 새로 만들지 않는다.** 예외 타입은 `CustomException` 하나이고, 구분은 `{도메인}ErrorCode` enum으로 한다. 코드 이름은 `{DOMAIN}_{SITUATION}` (예: `USER_NOT_FOUND`).
- **모든 Entity는 `BaseTimeEntity`를 상속한다.** `createdAt`/`updatedAt`을 직접 선언하지 않는다.
- **설정값은 `@ConfigurationProperties` + record로 받는다. `@Value` 금지.** `application.yml`에 `${ENV_VAR}`를 추가하면 루트 `.env.example`도 반드시 같이 갱신한다.
- **Entity 필드 변경은 반드시 Flyway 마이그레이션과 함께 한다.** `ddl-auto`는 `validate` 고정이므로 마이그레이션 없이 필드를 추가하면 기동이 실패한다. 이미 머지된 마이그레이션 파일은 수정하지 않고 새 버전을 추가한다.
- **Validation 메시지와 에러 메시지는 한글로 쓴다.**
- **`open-in-view: false`다.** 트랜잭션 밖에서 지연로딩하면 터진다. 필요한 데이터는 Service 안에서 다 꺼내 DTO로 반환한다.

## Flyway 마이그레이션

- 위치: `src/main/resources/db/migration/`
- 파일명: `V{번호}__{설명}.sql` (예: `V1__create_users.sql`) — 번호는 1부터 순차, 밑줄 두 개
- 머지된 파일은 절대 수정하지 않는다. 잘못된 스키마는 새 버전으로 고친다.
- 컬럼·테이블명은 `snake_case`, 테이블명은 복수형(`users`)
- 모든 테이블에 `created_at`, `updated_at`을 넣는다 (`BaseTimeEntity`와 대응)

## 코드 포맷

Spotless + google-java-format **AOSP 스타일**(4-space 인덴트, 100 컬럼). 커밋할 때 `.githooks/pre-commit`이 스테이징된 Java 파일만 자동 포맷한다. 훅 경로는 `./gradlew build` 시 `installGitHooks` task가 자동으로 걸어준다.

포맷을 손으로 맞추려 애쓰지 말고 `./gradlew spotlessApply`를 돌린다.

## Git

- 커밋: `type: 한글 제목` (type: `feat`/`fix`/`refactor`/`docs`/`test`/`chore`/`init`)
- 브랜치: `{type}/#{이슈번호}-{작업내용}` (예: `feat/#12-user-login`)
- PR 제목: `[{Type}/#{이슈번호}] 설명`
- 기본 브랜치는 `develop`, 배포 브랜치는 `main`. 둘 다 직접 push 금지.

## 멀티모듈 전환

지금은 단일 모듈이다. 도메인 경계가 분명해지고 빌드 시간·의존성 오염이 문제가 되면 아래 순서로 쪼갠다.

1. `global/`을 `common`(순수 유틸) + `core`(도메인) + `api`(웹) 로 분리
2. `domain/{도메인}` 패키지를 그대로 모듈로 승격하고 `settings.gradle.kts`에 `include`
3. 모듈 간 의존성은 `implementation`만 사용한다 (`api`는 의존성이 상위로 전파되어 레이어가 오염된다)

전환 비용을 낮추려면 지금 지켜야 할 것: **도메인 간 직접 참조 금지**(Service 경유), **`global`이 `domain`을 참조하지 않기**. 참고 구현: [DONGCHIMI-SERVER](https://github.com/TEAM-DONGCHIMI/DONGCHIMI-SERVER)

## 아직 없는 것

필요해지면 추가한다. 임의로 미리 넣지 않는다.

- 인증/인가 (Spring Security + JWT, 소셜 로그인)
- CI/CD 워크플로, Dockerfile, 배포 compose
- Redis, 파일 업로드(S3), 외부 API 클라이언트
- 테스트 전략 (Testcontainers 등)
