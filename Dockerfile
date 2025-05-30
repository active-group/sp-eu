# Build stage
FROM nixpkgs/nix-flakes@sha256:95bce4317c15dfab3babac5a6d19d3ed41e31a02a8aaf3d4f6639778cb763b0a AS builder

WORKDIR /build

COPY flake.nix .
COPY wisen/deps.edn .
COPY wisen/package*.json ./
COPY wisen/shadow-cljs.edn ./
COPY wisen/build.clj .
COPY wisen/src ./src
COPY wisen/public/schema ./public/schema

RUN nix develop -c bash -c "clj -T:build uber"

# Runtime stage
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=builder /build/target/uber/*-standalone.jar /app/app.jar
COPY --from=builder /build/public/schema /app/public/schema
COPY --from=builder /build/src/main/datashapesorg.ttl /app/datashapesorg.ttl

COPY ./start-app.sh /app/start-app.sh
RUN chmod +x /app/start-app.sh

CMD ["/app/start-app.sh"]
