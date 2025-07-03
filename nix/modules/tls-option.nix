lib:

let
  inherit (lib) types;
in
lib.mkOption {
  type = types.submodule {
    options = {
      enable = lib.mkEnableOption "proxy";
      domain = lib.mkOption {
        type = types.nonEmptyStr;
      };
      acme = lib.mkOption {
        type = types.bool;
        default = false;
        description = "Whether ACME should be enabled";
      };
      tlsCert = lib.mkOption {
        type = types.nullOr types.path;
        default = null;
      };
      tlsCertKey = lib.mkOption {
        type = types.nullOr types.path;
        default = null;
      };
    };
  };
}
