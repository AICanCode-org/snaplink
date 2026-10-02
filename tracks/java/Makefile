# SnapLink — Java track. Run `make help`.
MVN ?= mvn -B

.PHONY: help install lint test test-integration coverage run build
help:
	@echo "install | lint | test | test-integration | coverage | run | build"

install:
	$(MVN) -q dependency:go-offline

lint:
	$(MVN) checkstyle:check

test:
	$(MVN) test

test-integration:
	$(MVN) verify -Pintegration

coverage:
	$(MVN) verify -Pcoverage

# `make run` talks to the compose Redis by default; `REDIS_URL= make run` runs without a cache.
run: export REDIS_URL ?= redis://localhost:6379/0
run:
	$(MVN) spring-boot:run

build:
	docker build -f Dockerfile -t snaplink-java ../..
