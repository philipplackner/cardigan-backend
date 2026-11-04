FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY . .
RUN ./gradlew :app:bootJar --no-daemon --console=plain

FROM eclipse-temurin:25-jre
RUN useradd --system --no-create-home --shell /usr/sbin/nologin cardigan
WORKDIR /opt/cardigan
COPY --from=build /workspace/app/build/libs/app-*-SNAPSHOT.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
USER cardigan
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
