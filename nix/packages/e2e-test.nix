{ mkCljBin, lib, ... }:

mkCljBin {
  projectSrc = lib.cleanSource ../../wisen;
  name = "e2e-test-jar";
  main-ns = "wisen.e2e";
  buildCommand = "clj -T:build e2e-uber";
}
