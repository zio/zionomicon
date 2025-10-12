#!/usr/bin/env bash
# Validation script for Nix setup
# This script validates the Nix configuration without requiring Nix to be installed

set -e

echo "🔍 Validating Nix Configuration..."
echo ""

# Check if files exist
echo "✅ Checking if required files exist..."
files=(
  "flake.nix"
  "flake.lock"
  "shell.nix"
  ".envrc"
  "nix/sources.nix"
  "NIX_SETUP.md"
)

for file in "${files[@]}"; do
  if [ -f "$file" ]; then
    echo "  ✓ $file exists"
  else
    echo "  ✗ $file is missing"
    exit 1
  fi
done
echo ""

# Validate YAML syntax
echo "✅ Validating CI workflow YAML syntax..."
if command -v python3 &> /dev/null; then
  python3 -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"
  echo "  ✓ YAML syntax is valid"
else
  echo "  ⚠ Python3 not available, skipping YAML validation"
fi
echo ""

# Check if flake.nix has proper structure
echo "✅ Checking flake.nix structure..."
if grep -q "description" flake.nix && \
   grep -q "inputs" flake.nix && \
   grep -q "outputs" flake.nix && \
   grep -q "nixpkgs" flake.nix && \
   grep -q "devShells" flake.nix; then
  echo "  ✓ flake.nix has proper structure"
else
  echo "  ✗ flake.nix is missing required sections"
  exit 1
fi
echo ""

# Check shell.nix
echo "✅ Checking shell.nix structure..."
if grep -q "mkShell" shell.nix && \
   grep -q "buildInputs" shell.nix; then
  echo "  ✓ shell.nix has proper structure"
else
  echo "  ✗ shell.nix is missing required sections"
  exit 1
fi
echo ""

# Verify CI workflow uses Nix
echo "✅ Checking CI workflow uses Nix..."
if grep -q "install-nix-action" .github/workflows/ci.yml && \
   grep -q "nix develop" .github/workflows/ci.yml; then
  echo "  ✓ CI workflow uses Nix"
else
  echo "  ✗ CI workflow doesn't use Nix properly"
  exit 1
fi
echo ""

echo "🎉 All validations passed!"
echo ""
echo "To use the Nix environment:"
echo "  1. Install Nix: sh <(curl -L https://nixos.org/nix/install) --daemon"
echo "  2. Enable flakes: echo 'experimental-features = nix-command flakes' >> ~/.config/nix/nix.conf"
echo "  3. Enter environment: nix develop"
echo "  4. Or use direnv: direnv allow"
echo ""
echo "See NIX_SETUP.md for more details."
