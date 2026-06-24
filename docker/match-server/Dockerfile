# syntax=docker/dockerfile:1
#
# Tower Defense — standalone ephemeral match-server image (Phase 2).
#
# A single container == a single match. PaperMC boots, the Tower_Defense plugin builds the match
# world from the GAME_WORLD_TEMPLATES you bind-mount in (maps are NOT baked into the image — edit
# them on the host and drop them in anytime), and when the match reaches the ENDED lifecycle the
# entrypoint watchdog stops the server, so the container exits and the orchestrator can reap it.
#
#   build:  docker build -f docker/match-server/Dockerfile -t td-match:latest .
#           (the build context MUST be the repo root, not this folder)
#   run:    docker run --rm -p 25565:25565 \
#             -v /abs/path/GAME_WORLD_TEMPLATES:/opt/td-server/GAME_WORLD_TEMPLATES:ro \
#             td-match:latest
#
# NOTE on versions: the plugin targets Java 25 and Paper api-version 26.1.2
# (see pom.xml / plugin.yml) — NOT the Java 21 the original refactor note assumed.
# Both are overridable build args below.

ARG JAVA_VERSION=25
# Temurin only publishes -jre tags for LTS releases. 25 is non-LTS, so the runtime
# defaults to the JDK image. Override with --build-arg RUNTIME_IMAGE=eclipse-temurin:25-jre
# if/when a JRE image is available, or pin to an LTS for a smaller image.
ARG RUNTIME_IMAGE=eclipse-temurin:25-jdk
ARG PAPER_VERSION=26.1.2

# ---- Stage 1: compile the plugin -------------------------------------------
FROM maven:3-eclipse-temurin-${JAVA_VERSION} AS build
WORKDIR /build
# Resolve dependencies on their own layer so source edits don't re-download Paper.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline || true
COPY src ./src
RUN mvn -B -q clean package
# finalName is timestamped (tower-defense-<ts>.jar); normalise to a stable name.
RUN cp "$(ls -t target/tower-defense-*.jar | head -n1)" /tower-defense.jar

# ---- Stage 2: runtime ------------------------------------------------------
FROM ${RUNTIME_IMAGE} AS runtime
ARG PAPER_VERSION

ENV SERVER_DIR=/opt/td-server \
    CONSOLE_FIFO=/tmp/td-console \
    JVM_OPTS="-Xms1G -Xmx2G" \
    MATCH_END_GRACE_SECONDS=15

RUN apt-get update \
 && apt-get install -y --no-install-recommends bash curl jq ca-certificates \
 && rm -rf /var/lib/apt/lists/*

WORKDIR ${SERVER_DIR}

# Fetch the correct PaperMC build for ${PAPER_VERSION}. The latest build number is
# resolved from the download API at build time so the image always pins a real jar.
# If 26.1.2 is not served by api.papermc.io, override --build-arg PAPER_VERSION=<ver>.
RUN set -eux; \
    base="https://api.papermc.io/v2/projects/paper/versions/${PAPER_VERSION}"; \
    build="$(curl -fsSL "${base}/builds" | jq -r '.builds[-1].build')"; \
    jar="paper-${PAPER_VERSION}-${build}.jar"; \
    curl -fsSL -o "${SERVER_DIR}/paper.jar" "${base}/builds/${build}/downloads/${jar}"; \
    echo "eula=true" > "${SERVER_DIR}/eula.txt"

# Server config tuned for one ephemeral match behind a Velocity proxy.
COPY docker/match-server/server.properties ${SERVER_DIR}/server.properties

# Inject the compiled plugin.
COPY --from=build /tower-defense.jar ${SERVER_DIR}/plugins/Tower_Defense.jar

# Helper scripts (maps are bind-mounted at runtime, not bundled into the image).
COPY docker/match-server/match-watchdog.sh /opt/td/match-watchdog.sh
COPY docker/match-server/entrypoint.sh /opt/td/entrypoint.sh
RUN chmod +x /opt/td/entrypoint.sh

# Map templates are supplied by a read-only bind mount so they can be edited and dropped in without
# rebuilding. Declaring the mount point documents the contract (and gives an empty dir if unmounted).
VOLUME ["/opt/td-server/GAME_WORLD_TEMPLATES"]

EXPOSE 25565
ENTRYPOINT ["/opt/td/entrypoint.sh"]
