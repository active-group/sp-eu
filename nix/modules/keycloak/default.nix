{ config, lib, ... }:

let
  inherit (lib) types;
  cfg = config.active-group.keycloak;
  tls = import ../tls-option.nix lib;
in
{
  options.active-group.keycloak = {
    enable = lib.mkEnableOption "keycloak";
    realmFiles = lib.mkOption {
      type = types.listOf types.path;
    };
    dbPasswordFile = lib.mkOption {
      type = types.nullOr types.str;
      default = null;
    };
    inherit tls;
  };

  config = lib.mkIf cfg.enable {
    age.secrets.keycloak_db.file = ../../secrets/keycloak_db.age;

    active-group.acme.enable = cfg.tls.certs == "acme";

    services.keycloak = {
      enable = true;
      inherit (cfg) realmFiles;
      initialAdminPassword = "foobar";
      settings =
        {
          hostname = "localhost";
          http-port = 8080;
          http-enabled = true;
        }
        // (lib.optionalAttrs (cfg.tls != null) {
          hostname = "https://${cfg.tls.domain}/cloak";
          proxy-headers = "xforwarded";
        });
      database = {
        type = "postgresql";
        passwordFile =
          if cfg.dbPasswordFile == null then config.age.secrets.keycloak_db.path else cfg.dbPasswordFile;
        username = "keycloak-user";
        name = "keycloak";
      };
    };

    services.nginx = lib.mkIf (cfg.tls != null) {
      enable = true;
      virtualHosts.${cfg.tls.domain} =
        {
          locations = {
            "/cloak".return = "301 /cloak/";
            "/cloak/" = {
              proxyPass = "http://localhost:${toString config.services.keycloak.settings.http-port}";
              extraConfig = ''
                rewrite /cloak(.*)  $1                 break;
                proxy_set_header    Host               $host;
                proxy_set_header    X-Real-IP          $remote_addr;
                proxy_set_header    X-Forwarded-For    $proxy_add_x_forwarded_for;
                proxy_set_header    X-Forwarded-Host   $host;
                proxy_set_header    X-Forwarded-Server $host;
                proxy_set_header    X-Forwarded-Port   $server_port;
                proxy_set_header    X-Forwarded-Proto  $scheme;
              '';
            };
          };
          forceSSL = true;
        }
        // (
          if cfg.tls.certs == "acme" then
            { enableACME = cfg.tls.certs == "acme"; }
          else
            {
              sslCertificate = cfg.tls.certs.cert;
              sslCertificateKey = cfg.tls.certs.key;
            }
        );
    };
  };
}
