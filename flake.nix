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
    {
      self,
      nixpkgs,
      flake-parts,
      ...
    }@inputs:
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
        "aarch64-linux"
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
            dev-vm =
              if pkgs.stdenv.isLinux then
                inputs.self.nixosConfigurations."dev-${system}".config.system.build.vm
              else
                (
                  if pkgs.stdenv.system == "aarch64-darwin" then
                    pkgs.runCommand "macos-runner" { } ''
                      mkdir -p $out/bin
                      script=$out/bin/run
                      install -m 755 ${inputs.self.nixosConfigurations.dev-aarch64-linux.config.system.build.vm}/bin/run* $script
                      sed -i 's|/nix/store/.*/qemu|qemu|g' $script
                      sed -i 's|/nix/store/.*/mkfs|mkfs|g' $script
                      sed -i 's/^export PATH.*//g' $script
                      sed -i 's|/nix/store/.*/bash|/usr/bin/env bash|g' $script
                      sed -i 's|/nix/store/.*/mkfs|mkfs|g' $script
                      sed -i 's/kvm:tcg/hvf/g' $script
                    ''
                  else
                    builtins.throw "unexpected system"
                );
          };

          formatter = pkgs.nixfmt-rfc-style;

          # For the REPL and interactive debugging. Use `nix repl .#pkgs`.
          legacyPackages = pkgs;
        };

      flake = {
        nixosModules = {
          default = import ./nix/modules;
        };

        nixosConfigurations = builtins.listToAttrs (
          builtins.concatMap
            (
              system:
              let
                pkgs = mkPkgs system;
              in
              [
                {
                  name = "dev-${system}";
                  value = nixpkgs.lib.nixosSystem {
                    inherit pkgs system;
                    modules = [
                      inputs.self.nixosModules.default
                      ./nix/nixos-configurations/dev.nix
                    ];
                  };
                }
                {
                  name = "prod-${system}";
                  value = nixpkgs.lib.nixosSystem {
                    inherit pkgs system;
                    modules = [
                      inputs.self.nixosModules.default
                      ./nix/nixos-configurations/prod.nix
                    ];
                  };
                }
              ]
            )
            [
              "x86_64-linux"
              "aarch64-linux"
            ]
        );
      };
    };
}
