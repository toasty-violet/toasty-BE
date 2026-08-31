# toasty-BE

Toasty 서버. Java 21 + Spring Boot 3.5 + MySQL 8.


## 시작하기

**필요한 것**: JDK 21, Docker

```bash
# 1. 환경변수 파일 생성 (로컬 기본값이 채워져 있음)
cp .env.example .env

# 2. 로컬 MySQL 기동
docker compose -f docker/local/docker-compose.yml up -d

# 3. 실행
./gradlew bootRun
```

- API 문서(Swagger UI): http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

`.env` 없이도 `application.yml`의 기본값(로컬 MySQL)으로 뜨지만, 값을 바꿀 일이 생기면 `.env`를 쓴다. 서버 환경에서는 `.env` 대신 실제 환경변수를 주입한다.

## 명령어

| 명령 | 설명 |
| --- | --- |
| `./gradlew build` | 전체 빌드 (포맷 검사 포함) |
| `./gradlew bootRun` | 애플리케이션 실행 |
| `./gradlew test` | 테스트 |
| `./gradlew spotlessApply` | 코드 포맷 자동 수정 |
| `docker compose -f docker/local/docker-compose.yml up -d` | 로컬 MySQL 기동 |
| `docker compose -f docker/local/docker-compose.yml down` | 로컬 MySQL 중지 |

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| Language / Build | Java 21, Gradle 8.14 (Kotlin DSL) |
| Framework | Spring Boot 3.5 (Web MVC, Validation, Actuator) |
| ORM / Migration | Spring Data JPA, Flyway |
| Database | MySQL 8.0 |
| API 문서 | springdoc-openapi (Swagger UI) |
| 코드 스타일 | Spotless + google-java-format (AOSP) |

## 프로젝트 구조

```
src/main/java/com/toasty/
├── global/          # 공통 인프라 (응답 포맷, 예외 처리, 설정)
└── domain/          # 도메인별 패키지 (controller / service / entity / repository)

src/main/resources/
├── application.yml
└── db/migration/    # Flyway 마이그레이션

docker/local/        # 로컬 개발용 docker-compose
.githooks/           # pre-commit (Spotless 포맷 검사)
```

패키지 구조·레이어 규칙·네이밍·Flyway·Git 컨벤션은 **[CLAUDE.md](./CLAUDE.md)**

커밋 시 `.githooks/pre-commit`이 스테이징된 Java 파일을 자동 포맷한다. 훅은 `./gradlew build` 한 번 돌리면 자동으로 걸린다.
