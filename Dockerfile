# Hotel Booking Platform — container image.
#
# Explicitly the LAST thing added to this project and deliberately not the primary way to run
# it (design doc 16: "Docker — not in the rubric. Added last, only if tests and README are
# complete", and 13.1: "./gradlew bootRun must work first time"). Nothing else in this
# repository depends on this file; it exists so the application can be run somewhere that has
# a container runtime and no JDK, not to become the documented happy path.
#
# The database is unchanged: H2, in-memory, inside the container. There is deliberately no
# Compose file and no MySQL service here — 13.1's whole argument is that requiring a database
# to be stood up before the first request is reviewer friction, and adding one at this stage
# would reintroduce exactly what that decision avoided. The MySQL profile stays a reference
# artefact (see application-mysql.yml).

# ---------- build ----------
# Full JDK, because this stage compiles; the runtime stage below does not need one.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Wrapper and build scripts first, so dependency resolution lands in its own cached layer and
# is only redone when the build files change — not on every source edit.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies --configuration runtimeClasspath

COPY src ./src
# -x test: the suite is run by `./gradlew build` in development and in CI, not on every image
# build. An image build is not where a failing test should first be discovered, and running it
# here would add minutes to every build for a signal already gathered elsewhere.
RUN ./gradlew --no-daemon bootJar -x test

# ---------- runtime ----------
# JRE, not JDK: nothing at runtime compiles anything, and a smaller image has less in it to
# have a vulnerability in.
FROM eclipse-temurin:21-jre AS runtime

# curl exists solely for HEALTHCHECK below. Installed before dropping privileges, and the apt
# lists are removed in the same layer so they are not carried in the image.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Runs as a non-root user. The application needs no privileged operation whatsoever — it binds
# 8080 (unprivileged), writes no files, and keeps its entire database in memory.
RUN useradd --system --create-home --uid 10001 hotel
USER hotel
WORKDIR /home/hotel

COPY --from=build --chown=hotel:hotel /workspace/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

# The field-encryption key is read from the environment (see application.yml), so an image
# built from this file carries no key of its own. The committed development default is what
# makes `docker run` work with no arguments; a real deployment overrides it:
#     docker run -e ENCRYPTION_KEY="$(openssl rand -base64 32)" -p 8080:8080 hotel-booking
# Secrets belong in the environment or a secret manager, never baked into a layer — an ENV line
# here would put the key into the image's own metadata for anyone who pulls it.

# Polls the actuator health endpoint the application already exposes rather than inventing a
# probe: /actuator/health is enabled in application.yml and is what an orchestrator would use.
# start-period covers JVM and Spring startup, which is seconds, not milliseconds.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl --fail --silent http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
