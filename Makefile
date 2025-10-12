# Makefile for Zionomicon exercises
# Provides convenient commands for development

.PHONY: help nix-shell compile test clean lint fmt check publish validate-nix

help: ## Show this help message
	@echo 'Usage: make [target]'
	@echo ''
	@echo 'Available targets:'
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "  %-20s %s\n", $$1, $$2}'

# Nix commands
nix-shell: ## Enter Nix development shell
	nix develop

nix-direnv: ## Setup direnv for automatic environment loading
	@if command -v direnv >/dev/null 2>&1; then \
		direnv allow; \
		echo "✅ direnv configured. The environment will load automatically when entering this directory."; \
	else \
		echo "❌ direnv not installed. Install it first: https://direnv.net/"; \
		exit 1; \
	fi

# SBT commands (run within Nix environment)
compile: ## Compile the project
	nix develop -c sbt compile

test: ## Run tests
	nix develop -c sbt test

test-quick: ## Run tests for current Scala version only
	nix develop -c sbt test

clean: ## Clean build artifacts
	nix develop -c sbt clean

lint: ## Run linting checks
	nix develop -c sbt lint

fmt: ## Format code
	nix develop -c sbt scalafmtAll

fmt-check: ## Check code formatting
	nix develop -c sbt scalafmtCheckAll

check: ## Run all checks (compile, test, lint, format)
	nix develop -c sbt clean compile test lint scalafmtCheckAll

publish: ## Publish artifacts locally
	nix develop -c sbt publishLocal

ci-workflow-check: ## Check if CI workflow is up to date
	nix develop -c sbt ciCheckGithubWorkflow

# Validation
validate-nix: ## Validate Nix configuration
	./validate-nix-setup.sh

# Combined targets
dev: nix-shell ## Start development environment (alias for nix-shell)

ci: check ## Run full CI pipeline locally
