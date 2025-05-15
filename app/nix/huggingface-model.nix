{ pkgs ? import <nixpkgs> {}
, modelName ? "BAAI/bge-m3"
, modelRevision ? "5617a9f61b028005a4858fdac845db406aefb181"
}:

let
  pythonEnv = pkgs.python3.withPackages (ps: with ps; [
    torch
    transformers
  ]);

  safeModelName = builtins.replaceStrings ["/"] ["-"] modelName;

  conversionScript = ./convert_model.py;

in pkgs.stdenv.mkDerivation {
  pname = "${safeModelName}-torchscript";
  version = "1.0.0";

  dontUnpack = true;

  nativeBuildInputs = [ pythonEnv ];

  buildPhase = ''
    export HF_HOME="$TMPDIR/hf_home"
    mkdir -p $HF_HOME
    cp ${conversionScript} ./convert_model.py

    python ./convert_model.py --model-name "${modelName}" --model-revision "${modelRevision}"
  '';

  # outputHashMode = "recursive";
  # outputHashAlgo = "sha256";
  # outputHash = "sha256-iwV9YchzrzSnF95E5ZqDI2gI6oS2dA3KZQ3b8DCvk+I=";

  meta = with pkgs.lib; {
    description = "TorchScript version of ${modelName} for use with DJL";
    homepage = "https://huggingface.co/${modelName}";
    license = licenses.mit;
  };
}
