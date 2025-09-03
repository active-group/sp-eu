{
  stdenv,
  lib,
  python3,
  active-group,
  ...
}:

let
  inherit (active-group) modelConfig;
in
stdenv.mkDerivation {
  pname = "${modelConfig.safe-name}-torchscript";
  version = "1.0.0";
  src = null;
  dontUnpack = true;

  nativeBuildInputs = [
    (python3.withPackages (
      ps: with ps; [
        torch
        transformers
      ]
    ))
  ];

  buildPhase = ''
    export HF_HOME="$TMPDIR/hf_home"
    mkdir -p $HF_HOME

    python ${../../convert_model.py} \
      --model-name "${modelConfig.name}" \
      --model-revision "${modelConfig.revision}" \
      --traced-filename model.pt \
      --tokenizer-dirname tokenizer
  '';

  outputHashMode = "recursive";
  outputHashAlgo = "sha256";
  outputHash =
    if stdenv.system == "aarch64-darwin" then
      "sha256-rP9JEQziGiKmaEHHjD6pj8Sy0vJ+iQPgr77/qe1vCpY="
    else if stdenv.system == "x86_64-darwin" then
      "sha256-6vRLy5Li8zE1wSsHtqp65/svxuB6CkF7G/A2X7O6JRs="
    else
      "sha256-zDBIXOjoxKyicUwGAT6UoXC5ghE4YCAnxJpZ+2yYJ8M=";

  meta = with lib; {
    description = "TorchScript version of ${modelConfig.name} for use with DJL";
    homepage = "https://huggingface.co/${modelConfig.name}";
    license = licenses.mit;
  };
}
