# Nix Quick Reference

One-page quick reference for working with Nix in this repository.

## Setup (One-Time)

```bash
# Install Nix with flakes
sh <(curl -L https://nixos.org/nix/install) --daemon

# Enable flakes
echo "experimental-features = nix-command flakes" >> ~/.config/nix/nix.conf

# Optional: Install direnv for auto-loading
brew install direnv  # macOS
nix-env -i direnv    # Linux

# Add to shell rc file (~/.bashrc, ~/.zshrc, etc.)
eval "$(direnv hook bash)"  # or zsh, fish
```

## Daily Usage

### Method 1: direnv (Recommended)
```bash
cd /path/to/zionomicon
direnv allow                # First time only
# Environment loads automatically!
sbt compile
```

### Method 2: nix develop
```bash
nix develop             # Enter shell
sbt compile            # Run commands

# Or run directly
nix develop -c sbt compile
```

### Method 3: Makefile
```bash
make compile           # Compile
make test             # Test
make lint             # Lint
make check            # All checks
make ci               # Full CI locally
```

## Common Commands

| Command | Description |
|---------|-------------|
| `nix develop` | Enter dev environment |
| `nix develop -c <cmd>` | Run command in environment |
| `nix flake update` | Update dependencies |
| `nix flake check` | Validate flake |
| `nix develop .#ci` | Enter CI environment |
| `direnv allow` | Allow direnv auto-load |
| `direnv reload` | Reload environment |

## SBT Commands (Inside Nix Shell)

| Command | Description |
|---------|-------------|
| `sbt compile` | Compile code |
| `sbt test` | Run tests |
| `sbt +test` | Test all Scala versions |
| `sbt publishLocal` | Build artifacts |
| `sbt lint` | Run linting |
| `sbt scalafmtAll` | Format code |
| `sbt scalafmtCheckAll` | Check formatting |
| `sbt clean` | Clean build |

## Makefile Commands

| Command | Description |
|---------|-------------|
| `make help` | Show all commands |
| `make compile` | Compile project |
| `make test` | Run tests |
| `make lint` | Run linting |
| `make fmt` | Format code |
| `make check` | All checks |
| `make ci` | Full CI locally |
| `make validate-nix` | Validate Nix setup |

## Troubleshooting

### Problem: nix: command not found
**Solution**: Install Nix (see Setup above)

### Problem: experimental-features error
**Solution**: Enable flakes in `~/.config/nix/nix.conf`

### Problem: Slow first build
**Solution**: Normal! Nix downloads everything first time. Use Cachix for faster builds.

### Problem: direnv not working
**Solution**: Run `direnv allow` in project directory

### Problem: Environment not loading
**Solution**: Try `direnv reload` or restart shell

### Problem: Out of space
**Solution**: Clean Nix store: `nix-collect-garbage -d`

## File Overview

| File | Purpose |
|------|---------|
| `flake.nix` | Main Nix configuration |
| `flake.lock` | Dependency lock file |
| `shell.nix` | Legacy nix-shell support |
| `.envrc` | direnv configuration |
| `Makefile` | Convenient commands |
| `NIX_SETUP.md` | Full documentation |
| `NIX_MIGRATION.md` | Migration guide |

## Environment Info

To check your environment:
```bash
nix develop -c bash -c '
  echo "Java: $(java -version 2>&1 | head -1)"
  echo "SBT: $(sbt --version 2>&1 | grep script)"
  echo "JAVA_HOME: $JAVA_HOME"
'
```

## CI/CD

GitHub Actions automatically:
1. Installs Nix
2. Uses Cachix (optional)
3. Runs tests on Java 11, 17, 21
4. Uses same environment as local

## Resources

- [Nix Manual](https://nixos.org/manual/nix/stable/)
- [Nix Pills](https://nixos.org/guides/nix-pills/)
- [Zero to Nix](https://zero-to-nix.com/)
- [direnv](https://direnv.net/)

## Getting Help

1. Check `NIX_SETUP.md` for details
2. Run `./validate-nix-setup.sh` to check setup
3. Ask in GitHub issues/discussions
4. Nix community: https://discourse.nixos.org/

---

**Remember**: With Nix, your environment is reproducible everywhere! 🚀
