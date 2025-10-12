# Zionomicon Exercises - Nix Setup

This repository uses Nix for reproducible builds and consistent development environments across systems.

## Prerequisites

### Installing Nix

#### Linux and macOS

Install Nix with flakes support:

```bash
sh <(curl -L https://nixos.org/nix/install) --daemon
```

Enable flakes (add to `~/.config/nix/nix.conf` or `/etc/nix/nix.conf`):

```
experimental-features = nix-command flakes
```

## Quick Start

### Using Nix Flakes (Recommended)

Enter the development environment:

```bash
nix develop
```

Or run commands directly:

```bash
nix develop -c sbt compile
nix develop -c sbt test
```

### Using direnv (Optional but Recommended)

Install direnv:

```bash
# macOS
brew install direnv

# Linux
nix-env -i direnv
```

Add to your shell rc file (`.bashrc`, `.zshrc`, etc.):

```bash
eval "$(direnv hook bash)"  # or zsh, fish, etc.
```

Allow direnv in the project:

```bash
cd /path/to/zionomicon
direnv allow
```

The environment will automatically load when you enter the directory.

### Using Legacy nix-shell

For users without flakes:

```bash
nix-shell
```

## Development Workflow

Once in the Nix environment:

```bash
# Compile the project
sbt compile

# Run tests
sbt test

# Build artifacts locally
sbt publishLocal

# Check formatting
sbt scalafmtCheckAll

# Format code
sbt scalafmtAll

# Run linting
sbt lint
```

## CI/CD

The CI pipeline uses Nix for all builds, ensuring the same environment locally and in CI:

- **Build**: Compiles and publishes artifacts
- **Lint**: Checks code formatting and style
- **Test**: Runs tests on Java 11, 17, and 21

### Cachix

We use Cachix for binary caching to speed up CI builds. The cache is automatically populated on pushes to the main branch.

## Benefits of Nix

1. **Reproducibility**: Same build results across all systems
2. **Isolation**: No global dependency conflicts
3. **Consistency**: Identical dev and CI environments
4. **Declarative**: All dependencies declared in `flake.nix`
5. **Caching**: Fast builds with Nix store and Cachix

## Troubleshooting

### Flakes not working

Ensure flakes are enabled in your Nix configuration:

```bash
echo "experimental-features = nix-command flakes" >> ~/.config/nix/nix.conf
```

### Cache issues

Clear the Nix store cache:

```bash
nix-collect-garbage -d
```

### SBT issues

Clear SBT cache:

```bash
rm -rf ~/.sbt ~/.ivy2/cache ~/.cache/coursier
```

## Traditional Setup (Without Nix)

If you prefer not to use Nix, you can install dependencies manually:

- Java 17 (or 11, 21)
- SBT 1.10.11+
- libuv (for native dependencies)

However, Nix is highly recommended for consistency.

## Contributing

When contributing, please ensure your changes work with the Nix setup by testing with `nix develop`.

## Resources

- [Nix Manual](https://nixos.org/manual/nix/stable/)
- [Nix Flakes](https://nixos.wiki/wiki/Flakes)
- [direnv](https://direnv.net/)
- [Cachix](https://cachix.org/)
