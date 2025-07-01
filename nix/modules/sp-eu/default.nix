{
  config,
  lib,
  pkgs,
  ...
}:

let
  inherit (lib) types;
  cfg = config.active-group.sp-eu;
in
{
  options.active-group.sp-eu = {
    enable = lib.mkEnableOption "sp-eu";
    configFile = lib.mkOption {
      type = types.nullOr types.str;
      default = null;
    };
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
        assertion = cfg.proxy.acme -> (cfg.proxy.tlsCert == null && cfg.proxy.tlsCertKey == null);
        message = "Either use ACME *or* set TLS options, not both";
      }
      {
        assertion = !cfg.proxy.acme -> (cfg.proxy.tlsCert != null && cfg.proxy.tlsCertKey != null);
        message = "When not using ACME, you need to specify both TLS options";
      }
    ];

    systemd.services.sp-eu =
      let
        configArgs = lib.optionalString (cfg.configFile != null) "-c ${cfg.configFile}";
      in
      {
        environment = {
          TS_MODEL_NAME = pkgs.active-group.modelConfig.name;
          TS_MODEL_DIR = pkgs.active-group.embeddingModel;
        };
        wantedBy = [ "multi-user.target" ];
        after = [ "keycloak.service" ];
        description = "SP-EU service";
        serviceConfig = {
          User = "root";
          ExecStart = "${pkgs.jdk}/bin/java -jar ${pkgs.active-group.wisen}/lib/app.jar ${configArgs}";
          TimeoutStartSec = "0";
        };
      };

    active-group.acme.enable = cfg.proxy.acme;

    services.nginx = lib.mkIf cfg.proxy.enable {
      enable = true;
      virtualHosts.${cfg.proxy.domain} = {
        locations."/".proxyPass = "http://localhost:4321";
        forceSSL = true;
        enableACME = cfg.proxy.acme;
        sslCertificate = cfg.proxy.tlsCert;
        sslCertificateKey = cfg.proxy.tlsCertKey;
      };
    };
  };
}
