{
  config,
  lib,
  pkgs,
  ...
}:

let
  cfg = config.active-group.sp-eu;
in
{
  options.active-group.sp-eu = {
    enable = lib.mkEnableOption "sp-eu";
  };

  config = lib.mkIf cfg.enable {
    systemd.services.sp-eu = {
      wantedBy = [ "multi-user.target" ];
      after = [ "keycloak.service" ];
      description = "SP-EU service";
      serviceConfig = {
        User = "root";
        WorkingDirectory = "/root/wisen";
        ExecStart = "${pkgs.jdk}/bin/java -jar ${pkgs.active-group.wisen}/lib/app.jar";
        TimeoutStartSec = "0";
      };
    };
  };
}
