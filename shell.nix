# This file provides backwards compatibility for users not using flakes
# To use: nix-shell
# Or with direnv: add "use nix" to .envrc

let
  sources = import ./nix/sources.nix;
  pkgs = import sources.nixpkgs { };
in
pkgs.mkShell {
  buildInputs = with pkgs; [
    jdk17
    sbt
    libuv
    git
    coursier
  ];

  shellHook = ''
    echo "🚀 Zionomicon Development Environment (legacy nix-shell)"
    echo "Java version: $(java -version 2>&1 | head -n 1)"
    echo "SBT version: $(sbt --version 2>&1 | grep 'script version')"
    echo ""
    echo "Available commands:"
    echo "  sbt compile      - Compile the project"
    echo "  sbt test         - Run tests"
    echo "  sbt publishLocal - Build artifacts locally"
    echo ""
  '';

  JAVA_HOME = "${pkgs.jdk17}";
  SBT_OPTS = "-Xmx2G -XX:+UseG1GC";
}
