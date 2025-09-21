{
  config,
  lib,
  pkgs,
  ...
}:

let
  inherit (lib) types;
  cfg = config.active-group.sp-eu;
  tls = import ../tls-option.nix lib;
in
{
  options.active-group.sp-eu = {
    enable = lib.mkEnableOption "sp-eu";
    configFile = lib.mkOption {
      type = types.nullOr types.str;
      default = null;
    };
    inherit tls;
  };

  config = lib.mkIf cfg.enable {
    systemd.services.sp-eu =
      let
        configArgs = lib.optionalString (cfg.configFile != null) "-c ${cfg.configFile}";
      in
      {
        environment = {
          TS_MODEL_NAME = pkgs.active-group.modelConfig.name;
          TS_MODEL_DIR = pkgs.active-group.embeddingModel;
          PREFIX = "https://sp-eu.active-group.de";
          REPOSITORY = "/model.git";
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

    active-group.acme.enable = cfg.tls.certs == "acme";

    services.nginx = lib.mkIf (cfg.tls != null) {
      enable = true;
      virtualHosts.${cfg.tls.domain} =
        {
          locations."/".proxyPass = "http://localhost:4321";
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
