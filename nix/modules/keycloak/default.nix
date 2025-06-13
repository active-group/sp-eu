{
  config,
  lib,
  pkgs,
  ...
}:

let
  cfg = config.active-group.keycloak;
in
{
  options.active-group.keycloak = {
    enable = lib.mkEnableOption "keycloak";

    # TODO(Johannes): "Knowing" about prod vs. dev here kind of breaks the
    # modularization a bit (especially in regards to nginx, which needs to be
    # there for prod to work), but it's easiest to get something going. It
    # should also not need to change much.
    #
    # The best way would be one big SP-EU module with options for prod/dev and,
    # orthogonally, TLS, because we also want to be able to test something close
    # to the prod setup locally. Then we don't even need prod.nix and dev.nix.
    env = lib.mkOption {
      type = lib.types.enum [
        "prod"
        "dev"
      ];
      default = "prod";
    };
  };

  config = lib.mkIf cfg.enable {
    services.keycloak = {
      enable = true;
      realmFiles = if cfg.env == "prod" then [ ./prod/realm-export.json ] else [ ./dev/SP-EU-realm.json ];
      initialAdminPassword = "foobar";
      settings =
        {
          hostname = "localhost";
          http-port = 8080;
          http-enabled = true;
        }
        // (lib.optionalAttrs (cfg.env == "prod") {
          hostname = "https://sp-eu.ci.active-group.de/cloak";
          proxy-headers = "xforwarded";
        });
      database = {
        type = "mariadb";
        passwordFile = "${pkgs.writeTextFile {
          name = "db-password";
          text = "foobarpass";
        }}";
        username = "keycloak-user";
        name = "keycloak";
      };
    };
  };
}
