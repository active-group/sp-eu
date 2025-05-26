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
        };

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
        ];

        testPackages = [
          pkgs.chromium
          pkgs.chromedriver
        ];

        embeddingModel = pkgs.stdenv.mkDerivation {
          pname = "${modelConfig.safe-name}-torchscript";
          version = "1.0.0";

          dontUnpack = true;

          nativeBuildInputs = [
            (pkgs.python3.withPackages (ps: with ps; [
              torch
              transformers
            ]))
          ];

          buildPhase = ''
            export HF_HOME="$TMPDIR/hf_home"
            mkdir -p $HF_HOME

            python ${./convert_model.py} \
              --model-name "${modelConfig.name}" \
              --model-revision "${modelConfig.revision}" \
              --traced-filename "${modelConfig.traced-model-filename}" \
              --tokenizer-dirname "${modelConfig.tokenizer-dirname}"
          '';

          outputHashMode = "recursive";
          outputHashAlgo = "sha256";
          outputHash = "sha256-k59QI19kwPEcYWsuNF0ZzjbQhus1+HYR4SzcYHx3obk=";

          meta = with pkgs.lib; {
            description = "TorchScript version of ${modelConfig.name} for use with DJL";
            homepage = "https://huggingface.co/${modelConfig.name}";
            license = licenses.mit;
          };
        };

        tsModelPath = "${embeddingModel}/${modelConfig.traced-model-filename}";
        tsTokenizerPath = "${embeddingModel}/${modelConfig.tokenizer-dirname}";

        uberJar = pkgs.stdenv.mkDerivation {
          name = "wisen-uber-jar";

          src = ./wisen;

          buildInputs = self.devShells.${system}.default.buildInputs ++ [ pkgs.git pkgs.cacert ];

          buildPhase = ''
            export HOME=$(pwd)
            export JAVA_TOOL_OPTIONS="-Duser.home=$HOME"
            clj -T:build uber
          '';

          installPhase = ''
            mkdir -p $out/lib
            cp ./target/uber/*-standalone.jar $out/lib/app.jar
          '';
        };

        portableService = pkgs.stdenv.mkDerivation {
          name = "wisen-portable-service";

          dontUnpack = true;

          installPhase = ''
            mkdir -p $out/usr/bin                    # Executables
            mkdir -p $out/usr/lib/wisen              # Application libraries (app.jar)
            mkdir -p $out/usr/lib/wisen/jre          # Bundled Java runtime
            mkdir -p $out/usr/share/wisen/models     # Model files
            mkdir -p $out/etc/systemd/system         # Service definition
            mkdir -p $out/usr/lib/portable/profile   # Portable service metadata

            cp ${uberJar}/lib/app.jar $out/usr/lib/wisen/app.jar
            cp ${tsModelPath} $out/usr/share/wisen/models/
            cp -r ${tsTokenizerPath} $out/usr/share/wisen/models/
            cp -r ${pkgs.jdk}/* $out/usr/lib/wisen/jre/
            chmod -R +rX $out/usr/lib/wisen/jre/

            cat > $out/usr/bin/wisen <<EOF
            #!/bin/sh
            export TS_MODEL_NAME="${modelConfig.name}"
            export TS_MODEL_PATH="/usr/share/wisen/models/${modelConfig.traced-model-filename}"
            export TS_TOKENIZER_PATH="/usr/share/wisen/models/tokenizer"
            exec /usr/lib/wisen/jre/bin/java -jar /usr/lib/wisen/app.jar
            EOF
            chmod +x $out/usr/bin/wisen

            cat > $out/etc/systemd/system/wisen.service <<EOF
            [Unit]
            Description=SP-EU Wisen Service
            After=network.target

            [Service]
            Type=simple
            ExecStart=/usr/bin/wisen
            Restart=always
            RestartSec=10

            # Security hardening
            ProtectSystem=strict
            ProtectHome=yes
            PrivateTmp=yes
            NoNewPrivileges=yes
            StateDirectory=wisen

            [Install]
            WantedBy=multi-user.target
            EOF

            cat > $out/usr/lib/portable/profile/wisen.conf <<EOF
            [PortableService]
            PrimaryUnit=wisen.service
            PortableState=persistent
            PortableFlags=trusted
            EOF
          '';
        };

      in {
        devShells = {

          default = pkgs.mkShell {
            buildInputs = basePackages ++ [ embeddingModel ] ;

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
            CHROME_BIN = "${pkgs.lib.getExe pkgs.chromium}";
          };
        };

        packages = {
          default = portableService;
          embeddingModel = embeddingModel;
        };
      });
}
