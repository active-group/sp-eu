{
  description = "Convert HuggingFace model to TorchScript using Nix";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};
      in {
        packages.default = pkgs.callPackage ./huggingface-model.nix {
          modelName = "BAAI/bge-m3";
          modelRevision = "5617a9f61b028005a4858fdac845db406aefb181";
        };
      });
}
