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
            type = types.str;
          };
        };
      };
    };
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
        };
        wantedBy = [ "multi-user.target" ];
        after = [ "keycloak.service" ];
        description = "SP-EU service";
        serviceConfig = {
          User = "root";
          WorkingDirectory = "/root/wisen";
          ExecStart = "${pkgs.jdk}/bin/java -jar ${pkgs.active-group.wisen}/lib/app.jar ${configArgs}";
          TimeoutStartSec = "0";
        };
      };

    services.nginx = lib.mkIf cfg.proxy.enable {
      enable = true;
      virtualHosts.${cfg.proxy.domain} = {
        locations."/".proxyPass = "http://localhost:4321";
        enableACME = true;
        forceSSL = true;
      };
    };
  };
}
