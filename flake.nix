{
  description = "SP-EU app";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixpkgs-unstable";
    flake-parts.url = "github:hercules-ci/flake-parts";
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
          config.allowUnfree = true; # For google-chrome in devShells.
          overlays = [
            (final: prev: {
              active-group = {
                wisen = final.callPackage ./nix/packages/wisen-uberjar.nix { };
                embeddingModel = final.callPackage ./nix/packages/embedding-model.nix { inherit modelConfig; };
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
        { system, self', ... }:
        let
          pkgs = mkPkgs system;
          inherit (pkgs) lib stdenv;

          basePackages = [
            pkgs.clojure
            pkgs.nodejs
            pkgs.jdk
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
                pkgs.ollama
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
            inherit (pkgs.active-group) wisen embeddingModel;
            default = self'.packages.wisen;
          };

          formatter = pkgs.nixfmt-rfc-style;
        };

      flake =
        let
          inherit (nixpkgs) lib;
          system = "x86_64-linux";
          pkgs = mkPkgs system;
        in
        {
          nixosConfigurations = {
            prod = lib.nixosSystem {
              inherit pkgs system;
              modules = [ (import ./nix/nixos-configurations/prod.nix) ];
            };
          };
        };
    };
}
