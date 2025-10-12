# Nix Migration Summary

## Overview

This repository has been migrated to use Nix for reproducible builds and consistent development environments. This document provides a summary of changes and migration guidance.

## What Changed

### New Files Added

1. **flake.nix** - Modern Nix flake configuration with:
   - Development shell with Java 17, SBT, libuv, and coursier
   - CI-specific shell for GitHub Actions
   - Reproducible dependency pinning

2. **flake.lock** - Lock file pinning exact versions of Nix dependencies

3. **shell.nix** - Backwards-compatible Nix shell for non-flake users

4. **nix/sources.nix** - Nixpkgs pinning for legacy nix-shell

5. **.envrc** - direnv integration for automatic environment loading

6. **NIX_SETUP.md** - Comprehensive documentation for Nix setup and usage

7. **Makefile** - Convenient commands for common development tasks

8. **validate-nix-setup.sh** - Validation script for Nix configuration

9. **.nixignore** - Files to exclude from Nix builds

### Modified Files

1. **.github/workflows/ci.yml** - Updated CI pipeline to use Nix:
   - Uses `cachix/install-nix-action` for Nix installation
   - Runs all builds through `nix develop`
   - Supports Java 11, 17, and 21 testing
   - Optional Cachix integration for binary caching
   - Better caching strategy for SBT and Coursier

2. **.gitignore** - Added Nix-related entries:
   - `result` and `result-*` (Nix build outputs)
   - `.direnv` (direnv cache)

## Benefits

### 1. Reproducibility
- **Before**: Different developers might have different Java versions, SBT versions, or missing dependencies
- **After**: Everyone gets the exact same environment defined in flake.nix

### 2. Isolation
- **Before**: Global installations could conflict with other projects
- **After**: Each project has its own isolated environment

### 3. CI/Dev Parity
- **Before**: CI environment might differ from local development
- **After**: Identical environment in CI and locally

### 4. Easy Onboarding
- **Before**: New developers need to install Java, SBT, libuv manually
- **After**: Single command: `nix develop` or `direnv allow`

### 5. Declarative Dependencies
- **Before**: README instructions for manual setup
- **After**: All dependencies declared in code (flake.nix)

## Migration Guide for Developers

### Option 1: Quick Start with Nix Flakes (Recommended)

```bash
# Install Nix with flakes support
sh <(curl -L https://nixos.org/nix/install) --daemon

# Enable flakes
mkdir -p ~/.config/nix
echo "experimental-features = nix-command flakes" >> ~/.config/nix/nix.conf

# Enter development environment
cd /path/to/zionomicon
nix develop

# Or run commands directly
nix develop -c sbt compile
nix develop -c sbt test
```

### Option 2: With direnv (Most Convenient)

```bash
# Install direnv
# macOS: brew install direnv
# Linux: nix-env -i direnv

# Add to your shell rc file
eval "$(direnv hook bash)"  # or zsh, fish, etc.

# Allow direnv in project
cd /path/to/zionomicon
direnv allow

# Environment loads automatically!
sbt compile
sbt test
```

### Option 3: Legacy nix-shell

```bash
# For users without flakes enabled
nix-shell
sbt compile
```

### Option 4: Traditional Setup (Not Recommended)

You can still use traditional setup without Nix, but it's not recommended:
- Install Java 17 manually
- Install SBT 1.10.11+ manually
- Install libuv manually

## Using the Makefile

The Makefile provides convenient commands:

```bash
make help           # Show all available commands
make nix-shell      # Enter Nix shell
make compile        # Compile the project
make test           # Run tests
make lint           # Run linting
make fmt            # Format code
make check          # Run all checks
make ci             # Run full CI pipeline locally
make validate-nix   # Validate Nix configuration
```

## CI/CD Changes

### GitHub Actions Workflow

The CI pipeline now:
1. Installs Nix with flakes support
2. Optionally uses Cachix for binary caching
3. Runs all commands through `nix develop`
4. Tests on Java 11, 17, and 21

### Cachix Setup (Optional)

For faster CI builds, set up Cachix:

1. Create a Cachix cache at https://cachix.org/
2. Add `CACHIX_AUTH_TOKEN` secret to GitHub repository
3. CI will automatically push to cache

Note: CI works fine without Cachix, it just won't cache Nix store.

## Comparison: Before vs After

### Before (Traditional Setup)

```bash
# Developer needs to:
1. Install Java 17 (or 11, or 21)
2. Install SBT 1.10.11+
3. Install libuv
4. Hope it all works together
5. Debug version mismatches

# CI does:
1. apt-get install dependencies
2. actions/setup-java
3. sbt/setup-sbt
4. Hope it matches local environment
```

### After (Nix Setup)

```bash
# Developer needs to:
1. Install Nix once
2. nix develop (or direnv allow)
3. Everything just works

# CI does:
1. Install Nix
2. nix develop -c sbt <command>
3. Identical to local environment
```

## Technical Details

### Pinned Versions

- **Nixpkgs**: 24.05 stable channel
- **Java**: JDK 17 (default), JDK 11 and 21 for tests
- **SBT**: From nixpkgs (1.9.x+)
- **Scala**: 2.13.16 (from build.sbt)

### Environment Variables

The Nix shell sets:
- `JAVA_HOME`: Points to JDK installation
- `SBT_OPTS`: Optimized JVM settings (-Xmx2G -XX:+UseG1GC)

### Caching Strategy

1. **Nix Store**: Cached via Cachix (optional)
2. **SBT Dependencies**: Cached via GitHub Actions cache
3. **Coursier Cache**: Cached via GitHub Actions cache

## Troubleshooting

### "nix: command not found"

Install Nix or use traditional setup.

### "experimental-features" error

Enable flakes in Nix configuration.

### Slow first build

First build downloads and builds everything. Subsequent builds are fast due to caching.

### CI fails with Cachix

Cachix is optional. Remove the Cachix step or add the auth token secret.

## Rollback Plan

If you need to rollback to traditional setup:

1. The original CI workflow is preserved in git history
2. Traditional setup still works (install Java/SBT manually)
3. You can ignore all Nix files and use the project as before

However, we strongly recommend using Nix for consistency.

## Questions?

- See NIX_SETUP.md for detailed setup instructions
- Run `./validate-nix-setup.sh` to check your configuration
- Ask in GitHub issues or discussions

## Summary

The migration to Nix provides:
- ✅ Reproducible builds
- ✅ Consistent environments
- ✅ Easy onboarding
- ✅ CI/dev parity
- ✅ Declarative dependencies
- ✅ Better isolation
- ✅ Faster iteration with caching

Welcome to the reproducible future! 🚀
