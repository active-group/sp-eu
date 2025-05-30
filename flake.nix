{
  description = "SP-EU app";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixpkgs-unstable";
    flake-parts.url = "github:hercules-ci/flake-parts";
  };

  outputs =
    { nixpkgs, flake-parts, ... }@inputs:
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [
        "x86_64-linux"
        "aarch64-darwin"
      ];

      perSystem =
        { system, self', ... }:
        let
          pkgs = import nixpkgs {
            inherit system;
            config.allowUnfree = true; # For google-chrome in devShells.
          };
          inherit (pkgs) lib stdenv;

          modelConfig = {
            name = "BAAI/bge-m3";
            revision = "5617a9f61b028005a4858fdac845db406aefb181";
            safe-name = "BAAI-bge-m3";
            traced-model-filename = "BAAI-bge-m3-traced.pt";
            tokenizer-dirname = "tokenizer";
          };

          basePackages = [
            pkgs.clojure
            pkgs.nodejs
            pkgs.jdk
            pkgs.process-compose
            pkgs.ollama
            self'.formatter
          ];

          testPackages = [
            pkgs.chromium
            pkgs.chromedriver
          ];

          tsModelPath = "${self'.packages.embeddingModel}/${modelConfig.traced-model-filename}";
          tsTokenizerPath = "${self'.packages.embeddingModel}/${modelConfig.tokenizer-dirname}";
        in
        {
          devShells = {
            default = pkgs.mkShell {
              buildInputs = basePackages ++ [
                pkgs.process-compose
                self'.packages.embeddingModel
              ];

              shellHook = ''
                export TS_MODEL_NAME="${modelConfig.name}"
                export TS_MODEL_PATH=${tsModelPath}
                export TS_TOKENIZER_PATH=${tsTokenizerPath}

                # process-compose would use port 8080 by default, which we want to use instead
                export PC_PORT_NUM=8081
              '';
            };

            testWeb = pkgs.mkShell {
              buildInputs = basePackages ++ testPackages;
              # Needed in order to be able to start Chromium
              FONTCONFIG_FILE = pkgs.makeFontsConf { fontDirectories = [ ]; };
              CHROME_BIN = "${lib.getExe pkgs.chromium}";
            };
          };

          packages = {
            default = pkgs.callPackage ./nix/packages/wisen-uberjar.nix { };
            embeddingModel = pkgs.callPackage ./nix/packages/embedding-model.nix { inherit modelConfig; };
          };

          formatter = pkgs.nixfmt-rfc-style;
        };

      flake =
        let
          inherit (nixpkgs) lib;
          system = "x86_64-linux";
          pkgs = nixpkgs.legacyPackages.${system};
        in
        {
          nixosConfigurations = {
            foo = lib.nixosSystem {
              inherit pkgs system;
              modules = [
                (
                  { pkgs, lib, ... }:
                  {
                    environment.systemPackages = [ pkgs.git ];
                    virtualisation.vmVariant = {
                      virtualisation.forwardPorts = [
                        {
                          from = "host";
                          host.port = 2222;
                          guest.port = 22;
                        }
                      ];
                    };
                    services.openssh.enable = true;
                    users.users.alice = {
                      isNormalUser = true;
                      description = "Alice Foobar";
                      password = "foobar";
                      uid = 1000;
                    };
                  }
                )
              ];
            };
          };
        };
    };
}
