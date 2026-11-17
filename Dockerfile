# 백엔드 이미지. 운영 EC2(t4g, arm64)에서 직접 빌드한다
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar -x test && cp "$(ls build/libs/*.jar | grep -v plain)" /app.jar

FROM eclipse-temurin:17-jre
COPY --from=build /app.jar /app.jar
# 2GB 서버에 MySQL·Redis와 같이 올리므로 힙을 묶어 둔다
ENV JAVA_TOOL_OPTIONS="-Xmx448m -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
