# Build stage
FROM nixos/nix:2.26.3 AS builder

WORKDIR /build

COPY flake.nix .
COPY wisen/deps.edn .
COPY wisen/build.clj .
COPY wisen/src ./src
COPY wisen/public/schema ./public/schema
COPY wisen/package*.json ./
COPY wisen/shadow-cljs.edn ./

RUN echo "experimental-features = nix-command flakes" >> /etc/nix/nix.conf && \
    nix develop --command bash -c "clj -P && npm i && clj -T:build uber"

# Runtime stage
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

COPY --from=builder /build/target/uber/*-standalone.jar /app/app.jar
COPY --from=builder /build/public/schema /app/public/schema
COPY --from=builder /build/src/main/datashapesorg.ttl /app/datashapesorg.ttl

COPY ./start-app.sh /app/start-app.sh
RUN chmod +x /app/start-app.sh

CMD ["/app/start-app.sh"]
