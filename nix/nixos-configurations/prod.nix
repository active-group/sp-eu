{
  imports = [
    ./hardware-configuration.nix
    ./vm-base.nix
  ];

  boot.loader.grub = {
    enable = true;
    device = "/dev/sda";
  };

  networking = {
    hostName = "sp-eu";
    firewall = {
      enable = true;
      allowedTCPPorts = [
        22
        80
        443
      ];
    };
  };

  active-group = {
    sp-eu = {
      enable = true;
      proxy = {
        enable = true;
        domain = "sp-eu.ci.active-group.de";
      };
    };
    keycloak = {
      enable = true;
      realmFiles = [ ./keycloak-realms/realm-export.json ];
      proxy = {
        enable = true;
        domain = "sp-eu.ci.active-group.de";
      };
    };
  };

  security.acme = {
    acceptTerms = true;
    defaults.email = "admin@active-group.de";
  };

  users.users.root.openssh.authorizedKeys.keys = [
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIMkJUKlZpli3wOU3DLI6qEm1286CFOOmwnTUWOzalmMx ci@gitlab"
  ];

  system.stateVersion = "24.11";
}
