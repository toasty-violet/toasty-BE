# 빌드 단계. 의존성만 먼저 받아 레이어를 캐시한다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true
COPY src src
# 테스트는 CI가 이미 돌린다. 여기서 또 돌리면 DB가 필요해 빌드가 깨진다.
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV TZ=Asia/Seoul
# 컨테이너에 준 메모리를 기준으로 힙을 잡는다. 고정 -Xmx는 인스턴스를 바꿀 때마다 손대야 한다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"
RUN useradd --system --create-home toasty
COPY --from=build /build/build/libs/*.jar app.jar
USER toasty
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
