{ pkgs, ... }:

{
  services = {
    ollama = {
      enable = true;
      loadModels = [ "phi4" ];
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
