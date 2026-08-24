# CLAUDE.md

Java 21 + Spring Boot 3.5 단일 모듈 서버. 패키지 루트 `com.toasty`, DB는 MySQL 8.
프론트엔드는 별도 저장소([toasty-FE](https://github.com/toasty-violet/toasty-FE), Next.js, 3000 포트).

## 작업 원칙

- 확실하지 않으면 물어본다. 여러 해석이 가능하면 임의로 고르지 말고 선택지를 제시한다.
- 요청받은 범위만 수정한다. 인접 코드 개선·리팩토링은 하지 않는다.
- 테스트 코드는 명시적으로 요청받지 않으면 작성하지 않는다.
- 이 문서에 없는 패턴을 도입하거나 어느 패키지에 둘지 애매하면 먼저 확인한다.

## 구조

`global/`(config·entity·exception·response) + `domain/{도메인}/`(controller[/dto/request,response]·service·entity·repository·exception).
Command 객체는 `entity/`에 둔다.

- **다른 도메인의 `repository`를 직접 참조하지 않는다.** 상대 도메인의 `Service`를 통해서만 접근한다 (모듈 분리 대비).
- **`global`은 `domain`을 참조하지 않는다.**
- `@Transactional`은 Service에. 조회 전용은 `@Transactional(readOnly = true)`.
- Service가 비대해지거나 조회·검증 로직이 중복되면 **그 도메인에만** `implement/`를 두고 뽑아낸다. 미리 넣지 않고, 둔 도메인은 Repository 직접 접근과 섞지 않는다.

## 자주 틀리는 규칙

- **Request DTO를 Service로 넘기지 않는다.** Controller에서 `request.toCommand()`로 `{도메인}{동작}Command`로 변환한다.
- **응답은 항상 `ApiResponse`로 감싼다.** `ApiResponse.ok(data)` / `ApiResponse.ok()`. 실패 응답은 `GlobalExceptionHandler`에서만 만든다.
- **Controller에서 try-catch로 응답을 만들지 않는다.** `throw new CustomException(errorCode)`만 한다.
- **예외 클래스를 새로 만들지 않는다.** 타입은 `CustomException` 하나, 구분은 `{도메인}ErrorCode` enum. 코드명은 `{DOMAIN}_{SITUATION}` (예: `USER_NOT_FOUND`).
- **모든 Entity는 `BaseTimeEntity`를 상속한다.** `createdAt`/`updatedAt`을 직접 선언하지 않는다.
- **설정값은 `@ConfigurationProperties` + record. `@Value` 금지.** `application.yml`에 `${ENV_VAR}`를 추가하면 루트 `.env.example`도 같이 갱신한다.
- **Entity 필드 변경은 Flyway 마이그레이션과 함께 한다.** `ddl-auto`가 `validate` 고정이라 마이그레이션 없이 필드를 추가하면 기동이 실패한다.
- **`open-in-view: false`다.** 트랜잭션 밖 지연로딩은 터진다. 필요한 데이터는 Service 안에서 다 꺼내 DTO로 반환한다.
- **Validation·에러 메시지는 한글로 쓴다.**

## Flyway

`src/main/resources/db/migration/V{번호}__{설명}.sql` (밑줄 두 개, 1부터 순차).
머지된 파일은 절대 수정하지 않고 새 버전으로 고친다. 테이블명은 복수형 `snake_case`, 모든 테이블에 `created_at`·`updated_at`.

## 명령어

```bash
./gradlew bootRun                # 실행 (Swagger: /swagger-ui.html, Health: /actuator/health)
./gradlew build                  # 빌드 (spotlessCheck 포함)
./gradlew spotlessApply          # 포맷 자동 수정 — 손으로 맞추지 말 것
docker compose -f docker/local/docker-compose.yml up -d    # 로컬 MySQL
```

포맷은 Spotless + google-java-format **AOSP**(4-space, 100컬럼). 커밋 시 `.githooks/pre-commit`이 자동 실행된다.

## Git

- 커밋: `type: 한글 제목` (`feat`/`fix`/`refactor`/`docs`/`test`/`chore`/`init`)
- 브랜치: `{type}/#{이슈번호}-{작업내용}` (예: `feat/#12-user-login`)
- PR 제목: `[{Type}/#{이슈번호}] 설명`
- 기본 `develop`, 배포 `main`. 둘 다 직접 push 금지.
