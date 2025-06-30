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
    if stdenv.isDarwin then
      "sha256-rP9JEQziGiKmaEHHjD6pj8Sy0vJ+iQPgr77/qe1vCpY="
    else
      "sha256-zDBIXOjoxKyicUwGAT6UoXC5ghE4YCAnxJpZ+2yYJ8M=";

  meta = with lib; {
    description = "TorchScript version of ${modelConfig.name} for use with DJL";
    homepage = "https://huggingface.co/${modelConfig.name}";
    license = licenses.mit;
  };
}
