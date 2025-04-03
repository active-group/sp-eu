{
  description = "SP-EU app";

  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixpkgs-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true; # For google-chrome in devShells.
          # config.allowUnsupportedSystem = true;
        };

        basePackages = [
          pkgs.clojure
          pkgs.nodejs
          pkgs.jdk
          pkgs.process-compose
          pkgs.ollama
        ];

        testPackages = [
          pkgs.chromium
          pkgs.chromedriver
        ];
      in {
        devShells = {

          default = pkgs.mkShell {
            buildInputs = basePackages;

            shellHook = ''
            # process-compose would use port 8080 by default, which we want to use instead
            export PC_PORT_NUM=8081
          '';

          };

          testWeb = pkgs.mkShell {
            buildInputs = basePackages ++ testPackages;
            # Needed in order to be able to start Chromium
            FONTCONFIG_FILE = pkgs.makeFontsConf { fontDirectories = [ ]; };
            CHROME_BIN = "${pkgs.lib.getExe pkgs.chromium}";
          };
        };
      });
}
