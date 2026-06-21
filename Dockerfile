# ════════════════════════════════════════════════════════════
#  StudyLock — Dockerfile (multi-stage build)
# ════════════════════════════════════════════════════════════

# ── Stage 1: Build ────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies first
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Copy source and build (skip tests during image build; run via CI/`docker compose run test`)
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ── Stage 2: Runtime ───────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# Create non-root user
RUN groupadd -r studylock && useradd -r -g studylock studylock

# Copy the built jar
COPY --from=build /app/target/studylock-*.jar app.jar

# Directory for uploaded files (lectures, assignments, submissions, avatars)
RUN mkdir -p /app/uploads/lectures /app/uploads/assignments /app/uploads/submissions /app/uploads/avatars /app/uploads/quizzes /app/uploads/quiz_answers \
    && chown -R studylock:studylock /app

USER studylock

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
