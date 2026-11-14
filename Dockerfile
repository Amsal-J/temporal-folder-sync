# ------------ Stage 1: Build ------------
FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /build

# Download dependencies first (chached unless pom.xml changes)
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy the code and build it
COPY src ./src
RUN mvn -B  package -DskipTests \
&& mvn -B dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib

# ------------ Stage 2: Run ------------
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /build/target/*.jar ./app.jar
COPY --from=build /build/target/lib ./lib

ENTRYPOINT ["java", "-cp", "app.jar:lib/*"]
CMD ["foldersync.FolderSyncWorker"]