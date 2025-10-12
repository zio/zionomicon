{
  description = "Zionomicon exercises - ZIO learning material";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.05";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
        
        jdk = pkgs.jdk17;
        sbt = pkgs.sbt;
        
        buildInputs = [
          jdk
          sbt
          pkgs.libuv
          pkgs.git
          pkgs.coursier
        ];
        
      in
      {
        devShells.default = pkgs.mkShell {
          inherit buildInputs;
          
          shellHook = ''
            echo "🚀 Zionomicon Development Environment"
            echo "Java version: $(java -version 2>&1 | head -n 1)"
            echo "SBT version: $(sbt --version 2>&1 | grep 'script version')"
            echo ""
            echo "Available commands:"
            echo "  sbt compile      - Compile the project"
            echo "  sbt test         - Run tests"
            echo "  sbt publishLocal - Build artifacts locally"
            echo ""
          '';
          
          JAVA_HOME = "${jdk}";
          SBT_OPTS = "-Xmx2G -XX:+UseG1GC";
        };
        
        # CI-specific shell for GitHub Actions
        devShells.ci = pkgs.mkShell {
          inherit buildInputs;
          
          shellHook = ''
            echo "CI Environment initialized"
            export JAVA_HOME="${jdk}"
            export SBT_OPTS="-Xmx2G -XX:+UseG1GC"
          '';
        };
      }
    );
}
