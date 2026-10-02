FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
RUN mkdir -p /certs && \
    openssl s_client -connect repo.maven.apache.org:443 -servername repo.maven.apache.org -showcerts </dev/null 2>/dev/null | \
    awk '/BEGIN CERT/{c++} c{print > "/certs/cert" c ".pem"}' && \
    for f in /certs/cert*.pem; do \
      keytool -importcert -noprompt -alias "corp-$(basename "$f" .pem)" -file "$f" -cacerts -storepass changeit || true; \
    done
COPY settings.xml pom.xml ./
RUN mvn -s settings.xml -q dependency:go-offline
COPY src ./src
RUN mvn -s settings.xml -q clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/social-network.jar ./social-network.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "social-network.jar"]
