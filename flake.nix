{
  description = "SP-EU app";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixpkgs-unstable";
    flake-parts.url = "github:hercules-ci/flake-parts";
    clj-nix = {
      url = "github:jlesquembre/clj-nix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    { nixpkgs, flake-parts, ... }@inputs:
    let
      modelConfig = {
        name = "BAAI/bge-m3";
        revision = "5617a9f61b028005a4858fdac845db406aefb181";
        safe-name = "BAAI-bge-m3";
        traced-model-filename = "BAAI-bge-m3-traced.pt";
        tokenizer-dirname = "tokenizer";
      };
      mkPkgs =
        system:
        import nixpkgs {
          inherit system;
          config.allowUnfree = true;
          overlays = [
            inputs.clj-nix.overlays.default
            (final: prev: {
              active-group = {
                wisen = final.callPackage ./nix/packages/wisen-uberjar.nix { };
                embeddingModel = final.callPackage ./nix/packages/embedding-model.nix { inherit modelConfig; };
                npmDeps = final.importNpmLock.buildNodeModules {
                  inherit (final) nodejs;
                  npmRoot = ./.;
                };
                cljDeps = final.mk-deps-cache {
                  lockfile = ./deps-lock.json;
                };
              };
            })
          ];
        };
    in
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [
        "x86_64-linux"
        "aarch64-darwin"
      ];

      perSystem =
        {
          system,
          self',
          inputs',
          ...
        }:
        let
          pkgs = mkPkgs system;
          inherit (pkgs) lib stdenv;

          basePackages = [
            pkgs.clojure
            pkgs.nodejs
            pkgs.jdk
            self'.formatter
          ];

          tsModelPath = "${self'.packages.embeddingModel}/${modelConfig.traced-model-filename}";
          tsTokenizerPath = "${self'.packages.embeddingModel}/${modelConfig.tokenizer-dirname}";
        in
        {
          devShells = {
            default = pkgs.mkShell {
              inherit (pkgs.active-group) npmDeps;

              TS_MODEL_NAME = "${modelConfig.name}";
              TS_MODEL_PATH = "${tsModelPath}";
              TS_TOKENIZER_PATH = "${tsTokenizerPath}";
              PC_PORT_NUM = "8081";

              buildInputs = basePackages ++ [
                pkgs.importNpmLock.hooks.linkNodeModulesHook
                pkgs.ollama
                pkgs.process-compose
                self'.packages.embeddingModel
              ];
            };

            testWeb = pkgs.mkShell {
              buildInputs = basePackages ++ [
                pkgs.chromium
                pkgs.chromedriver
              ];
              # Needed in order to be able to start Chromium
              FONTCONFIG_FILE = pkgs.makeFontsConf { fontDirectories = [ ]; };
              CHROME_BIN = "${lib.getExe pkgs.chromium}";
            };
          };

          packages = {
            inherit (pkgs.active-group) wisen embeddingModel;
            default = self'.packages.wisen;
            update-clj-lockfile = inputs'.clj-nix.packages.deps-lock;
          };

          formatter = pkgs.nixfmt-rfc-style;

          # For the REPL and interactive debugging. Use `nix repl .#pkgs`.
          legacyPackages = pkgs;
        };

      flake =
        let
          inherit (nixpkgs) lib;
          system = "x86_64-linux";
          pkgs = mkPkgs system;
        in
        {
          nixosModules = {
            default = import ./nix/modules;
          };

          nixosConfigurations = {
            dev = lib.nixosSystem {
              inherit pkgs system;
              modules = [
                ./nix/nixos-configurations/dev.nix
              ];
            };
            prod = lib.nixosSystem {
              inherit pkgs system;
              modules = [
                inputs.self.nixosModules.default
                ./nix/nixos-configurations/prod.nix
              ];
            };
          };
        };
    };
}
