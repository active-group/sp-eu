{ pkgs, modelConfig, ... }:

pkgs.stdenv.mkDerivation {
  pname = "${modelConfig.safe-name}-torchscript";
  version = "1.0.0";
  src = null;
  dontUnpack = true;

  nativeBuildInputs = [
    (pkgs.python3.withPackages (
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
      --traced-filename "${modelConfig.traced-model-filename}" \
      --tokenizer-dirname "${modelConfig.tokenizer-dirname}"
  '';

  outputHashMode = "recursive";
  outputHashAlgo = "sha256";
  outputHash =
    if pkgs.stdenv.isDarwin then
      "sha256-k59QI19kwPEcYWsuNF0ZzjbQhus1+HYR4SzcYHx3obk="
    else
      "sha256-soZ8bGWlA/7gf9UbNSAQtUpj4hxPMhRCqUkXMkUg+xA=";

  meta = with pkgs.lib; {
    description = "TorchScript version of ${modelConfig.name} for use with DJL";
    homepage = "https://huggingface.co/${modelConfig.name}";
    license = licenses.mit;
  };
}
