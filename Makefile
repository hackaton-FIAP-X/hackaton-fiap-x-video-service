MAVEN_IMAGE := maven:3.9-eclipse-temurin-21
MAVEN_RUN := docker run --rm -v "$(CURDIR)":/app -v fiapx-maven-cache:/root/.m2 -w /app $(MAVEN_IMAGE)

.PHONY: up down logs build test format clean

up:
	docker compose up -d --build

down:
	docker compose down

logs:
	docker compose logs -f video-service

build:
	$(MAVEN_RUN) mvn -B verify

test:
	$(MAVEN_RUN) mvn -B test

format:
	$(MAVEN_RUN) mvn -B spotless:apply

clean:
	docker compose down -v
	$(MAVEN_RUN) mvn -B clean
