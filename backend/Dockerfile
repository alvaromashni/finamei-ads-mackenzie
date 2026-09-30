# syntax=docker/dockerfile:1
#
# Imagem do backend para o Render (free tier: 512 MB de RAM e pouca CPU).
# Build: docker build -t finamei-api backend
# Guia de deploy: docs/deploy-render.md

# ---------- 1. Build: compila e separa o jar em camadas ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependências primeiro: só são baixadas de novo quando o pom.xml muda.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q package -DskipTests \
    && cp target/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---------- 2. Runtime: só o JRE, sem Maven nem código-fonte ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S finamei && adduser -S finamei -G finamei
USER finamei

# Camadas da menos para a mais alterada, para aproveitar o cache do Docker.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

# JVM ajustada para caber com folga em 512 MB:
#   MaxRAMPercentage=50     heap de até ~256 MB; o resto fica para metaspace, threads e código
#   UseSerialGC             coletor mais leve, adequado a uma CPU só
#   TieredStopAtLevel=1     só o compilador C1: menos memória e inicialização mais rápida
#   Xss512k                 pilha menor por thread
#   ExitOnOutOfMemoryError  se faltar memória, o processo cai e o Render reinicia
# Pode ser sobrescrito pela variável JAVA_TOOL_OPTIONS no painel do Render.
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -XX:+ExitOnOutOfMemoryError"

# O Render informa a porta pela variável PORT (application-prod.yml).
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
