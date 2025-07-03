lib:

let
  inherit (lib) types;
in
lib.mkOption {
  default = null;
  type = types.nullOr (
    types.submodule {
      options = {
        domain = lib.mkOption { type = types.nonEmptyStr; };
        certs = lib.mkOption {
          type = types.oneOf [
            (types.enum [ "acme" ])
            (types.submodule {
              options = {
                cert = lib.mkOption { type = types.path; };
                key = lib.mkOption { type = types.path; };
              };
            })
          ];
        };
      };
    }
  );
}
