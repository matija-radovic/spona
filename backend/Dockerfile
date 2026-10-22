FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/
# The cache mount keeps Maven and the dependencies between builds, so a code change does not download them again
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app

RUN groupadd --system spona && useradd --system --gid spona spona

COPY --from=build /app/target/*.jar app.jar
RUN chown spona:spona /app/app.jar
USER spona

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
