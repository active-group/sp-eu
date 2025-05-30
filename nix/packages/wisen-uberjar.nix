{ pkgs, ... }:

pkgs.stdenv.mkDerivation {
  name = "wisen-uber-jar";

  src = ../../wisen;

  buildInputs = [
    pkgs.clojure
    pkgs.nodejs
    pkgs.jdk
    pkgs.git
    pkgs.cacert
  ];

  buildPhase = ''
    export HOME=$(pwd)
    export JAVA_TOOL_OPTIONS="-Duser.home=$HOME"
    clj -T:build uber
  '';

  installPhase = ''
    mkdir -p $out/lib
    cp ./target/uber/*-standalone.jar $out/lib/app.jar
  '';
}
