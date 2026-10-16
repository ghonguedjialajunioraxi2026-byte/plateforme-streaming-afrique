# ============================================================
# Streaming Afrique — backend Spring Boot (Java 17)
# Image multi-étapes :
#   1. build → compile le jar avec Maven (JDK 17)
#   2. run   → exécute le jar sur un JRE 17 allégé, sans Maven
#
# AUCUN secret dans cette image : DB_* / JWT_SECRET / REDIS_*
# sont fournis à l'EXÉCUTION (docker -e, docker-compose.yml,
# fichier .env), JAMAIS via ENV ici. Sans eux, le profil prod
# refuse de démarrer (échec rapide = comportement voulu,
# voir src/main/resources/application-prod.yml).
#
# Construction (depuis la racine du dépôt) :
#   docker build -t streaming-afrique-backend:6.31 ./backend
# ============================================================

# ---------- Étape 1 : construction ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Le pom d'abord, seul : tant que pom.xml ne change pas, Docker
# réutilise le cache des dépendances → builds suivants rapides.
COPY pom.xml .
RUN mvn -q dependency:go-offline

# Puis les sources : compilation + jar exécutable Spring Boot.
# (-DskipTests : les tests tournent en CI/dev via `mvn test`,
#  on ne les rejoue pas à chaque construction d'image.)
COPY src ./src
RUN mvn -q package -DskipTests \
 && java -Djarmode=layertools -jar target/backend-0.0.1-SNAPSHOT.jar extract --destination target/extracted

# ---------- Étape 2 : exécution ----------
FROM eclipse-temurin:17-jre

LABEL org.opencontainers.image.title="streaming-afrique-backend" \
      org.opencontainers.image.description="API de la plateforme africaine de livestreaming (Spring Boot, Java 17)"

# curl sert UNIQUEMENT à la sonde HEALTHCHECK ci-dessous.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# Utilisateur non-root : si le processus applicatif était compromis,
# il n'écrirait ni ne lirait en root dans le conteneur.
RUN useradd --create-home --shell /usr/sbin/nologin app
WORKDIR /app

# Copie par couches Spring Boot (dependencies → loader → snapshots →
# application) : une recompilation ne réécrit que la couche applicative.
COPY --from=build --chown=app:app /build/target/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/target/extracted/application/ ./

# Dossiers de données (en dev, docker-compose.yml les monte en volumes).
RUN mkdir -p /app/uploads /app/logs && chown -R app:app /app/uploads /app/logs
USER app

# Profil par défaut = prod (sécurisé : secrets obligatoires, ddl-auto none).
# docker-compose.yml le surcharge en "dev" pour le poste local.
# TZ = horodatage des logs en UTC, comme la config Jackson (application.yml).
ENV SPRING_PROFILES_ACTIVE=prod \
    TZ=UTC

EXPOSE 8080

# Sonde Docker : /actuator/health est PUBLIC (voir SecurityConfig),
# start-period large = le temps du démarrage Spring + Hibernate.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1

# Astuce JVM : pour passer des options (-Xmx…), définir JAVA_TOOL_OPTIONS
# à l'exécution — la JVM le lit automatiquement (ex. -e JAVA_TOOL_OPTIONS="-Xmx512m").
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
