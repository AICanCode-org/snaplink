# SnapLink — Python track. Run `make help`.
PY ?= python3
VENV ?= .venv
BIN := $(VENV)/bin

.PHONY: help install lint test test-integration coverage run build
help:
	@echo "install | lint | test | test-integration | coverage | run | build"

$(BIN)/activate:
	$(PY) -m venv $(VENV)

install: $(BIN)/activate
	$(BIN)/pip install -q -r requirements-dev.txt

lint:
	$(BIN)/ruff check app tests
	$(BIN)/ruff format --check app tests

test:
	$(BIN)/pytest

test-integration:
	$(BIN)/pytest -m integration

coverage:
	$(BIN)/pytest -m "integration or not integration" --cov=app --cov-report=term-missing --cov-report=xml --cov-fail-under=85

# `make run` talks to the compose Redis by default; `REDIS_URL= make run` runs without a cache.
run: export REDIS_URL ?= redis://localhost:6379/0
run:
	$(BIN)/python -m app

build:
	docker build -f Dockerfile -t snaplink-python ../..
