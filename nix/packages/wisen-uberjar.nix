{
  active-group,
  stdenv,
  lib,
  clj-builder,
  clojure,
  nodejs,
  importNpmLock,
  ...
}:

let
  inherit (active-group)
    cljDeps
    npmDeps
    embeddingModel
    modelConfig
    ;
in
stdenv.mkDerivation {
  name = "wisen-uber-jar";

  src = lib.cleanSource ../../wisen;

  inherit npmDeps;
  nativeBuildInputs = [
    clojure
    nodejs
    clj-builder
    importNpmLock.linkNodeModulesHook
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

    clj -T:build uber
  '';

  installPhase = ''
    mkdir -p $out/lib
    cp ./target/uber/*-standalone.jar $out/lib/app.jar
  '';
}
