# SnapLink — TypeScript track. Run `make help`.
.PHONY: help install lint test test-integration coverage run build
help:
	@echo "install | lint | test | test-integration | coverage | run | build"

install:
	npm ci --no-audit --no-fund

lint:
	npm run -s lint

test:
	npm test

test-integration:
	npm run test:integration

coverage:
	npm run coverage

# `make run` talks to the compose Redis by default; `REDIS_URL= make run` runs without a cache.
run: export REDIS_URL ?= redis://localhost:6379/0
run:
	npm run dev

build:
	npm run build
	docker build -f Dockerfile -t snaplink-typescript ../..
