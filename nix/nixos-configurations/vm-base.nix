{ pkgs, ... }:

{
  services = {
    ollama = {
      enable = true;
      loadModels = [ "mistral" ];
      host = "0.0.0.0";
    };

    riemann-tools = {
      enableHealth = true;
      riemannHost = "riemann.active-group.de";
    };

    openssh = {
      enable = true;
      settings.PermitRootLogin = "yes";
    };
  };

  nix = {
    package = pkgs.nixVersions.stable;
    extraOptions = "experimental-features = nix-command flakes";
  };
}
