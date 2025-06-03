{
  pkgs,
  lib,
  clj-builder,
  clojure,
  nodejs,
  ...
}:

let
  inherit (pkgs.active-group) cljDeps npmDeps;
in
pkgs.stdenv.mkDerivation {
  name = "wisen-uber-jar";

  src = lib.cleanSource ../..;

  nativeBuildInputs = [
    clojure
    nodejs
    clj-builder
  ];

  preBuildPhases = [ "preBuildPhase" ];
  preBuildPhase = "clj-builder patch-git-sha $(pwd)";
  buildPhase = ''
    export HOME=$(pwd)
    export JAVA_TOOL_OPTIONS="-Duser.home=$HOME"

    ln -s ${cljDeps}/.m2 .m2
    ln -s ${cljDeps}/.gitlibs .gitlibs
    ln -s ${cljDeps}/.clojure .clojure
    export GITLIBS="$HOME/.gitlibs"
    export CLJ_CONFIG="$HOME/.clojure"
    export CLJ_CACHE="$TMP/cp_cache"

    ln -s ${npmDeps}/node_modules .

    clj -T:build uber
  '';

  installPhase = ''
    mkdir -p $out/lib
    cp ./target/uber/*-standalone.jar $out/lib/app.jar
  '';
}
