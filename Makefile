MAVEN_IMAGE := maven:3.9-eclipse-temurin-21
# O docker-java assume API 1.32 por padrao, abaixo do minimo do Docker moderno.
DOCKER_API_VERSION ?= $(shell docker version --format '{{.Server.APIVersion}}' 2>/dev/null)
MAVEN_RUN := docker run --rm -v "$(CURDIR)":/app -v fiapx-maven-cache:/root/.m2 -w /app $(MAVEN_IMAGE)

# Os testes de integracao sobem containers via Testcontainers, entao o container
# do Maven precisa falar com o Docker do host e alcancar as portas publicadas.
MAVEN_RUN_IT := docker run --rm --network host \
	-v "$(CURDIR)":/app -v fiapx-maven-cache:/root/.m2 \
	-v /var/run/docker.sock:/var/run/docker.sock \
	-e TESTCONTAINERS_RYUK_DISABLED=true \
	-e DOCKER_HOST=unix:///var/run/docker.sock \
	-e DOCKER_API_VERSION=$(DOCKER_API_VERSION) \
	-w /app $(MAVEN_IMAGE)

.PHONY: up down logs build test format coverage clean

up:
	docker compose up -d --build

down:
	docker compose down

logs:
	docker compose logs -f video-service

build:
	$(MAVEN_RUN_IT) mvn -B verify

test:
	$(MAVEN_RUN_IT) mvn -B test

coverage: build
	@echo "Relatorio: target/site/jacoco/index.html"

format:
	$(MAVEN_RUN) mvn -B spotless:apply

clean:
	docker compose down -v
	$(MAVEN_RUN) mvn -B clean
