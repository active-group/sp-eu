{
  config,
  lib,
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
    virtualisation.oci-containers.containers.keycloak = {
      image = "quay.io/keycloak/keycloak@sha256:044a457e04987e1fff756be3d2fa325a4ef420fa356b7034ecc9f1b693c32761";
      ports = [ "8080:8080" ];
      volumes = [
        "${./${cfg.env}}:/opt/keycloak/data/import"
      ];
      environment =
        {
          KC_BOOTSTRAP_ADMIN_USERNAME = "admin";
          KC_BOOTSTRAP_ADMIN_PASSWORD = "agkeycloac08";
        }
        // (lib.optionalAttrs (cfg.env == "prod") {
          KC_PROXY_HEADERS = "xforwarded";
          KC_PROXY = "edge";
          # FIXME(Johannes): parameterize over domain
          KC_HOSTNAME = "https://sp-eu.ci.active-group.de/cloak";
        });
      cmd = [
        "start"
        "--http-enabled=true"
        "--import-realm"
      ];
    };
  };
}
