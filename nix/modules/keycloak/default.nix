{ config, lib, ... }:

let
  cfg = config.active-group.keycloak;
  inherit (lib) types;
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
    # TODO(Johannes): refactor into lib (together with the assertions below)
    proxy = lib.mkOption {
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
    };
  };

  config = lib.mkIf cfg.enable {
    assertions = [
      {
        assertion =
          cfg.proxy.enable && cfg.proxy.acme -> (cfg.proxy.tlsCert == null && cfg.proxy.tlsCertKey == null);
        message = "Either use ACME *or* set TLS options, not both";
      }
      {
        assertion =
          cfg.proxy.enable && !cfg.proxy.acme
          -> (cfg.proxy.enable && cfg.proxy.tlsCert != null && cfg.proxy.tlsCertKey != null);
        message = "When not using ACME, you need to specify both TLS options";
      }
    ];

    age.secrets.keycloak_db.file = ../../secrets/keycloak_db.age;

    active-group.acme.enable = cfg.proxy.acme;

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
        // (lib.optionalAttrs cfg.proxy.enable {
          hostname = "https://${cfg.proxy.domain}/cloak";
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

    services.nginx = lib.mkIf cfg.proxy.enable {
      enable = true;
      virtualHosts.${cfg.proxy.domain} = {
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
        enableACME = cfg.proxy.acme;
        sslCertificate = cfg.proxy.tlsCert;
        sslCertificateKey = cfg.proxy.tlsCertKey;
      };
    };
  };
}
