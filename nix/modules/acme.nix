{ config, lib, ... }:

{
  options.active-group.acme = {
    enable = lib.mkEnableOption "acme";
  };

  config = lib.mkIf config.active-group.acme.enable {
    security.acme = {
      acceptTerms = true;
      defaults.email = "admin@active-group.de";
    };
  };
}
