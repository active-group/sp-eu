{ config, ... }:

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

  age.secrets.config_edn.file = ../secrets/prod_config_edn.age;
  age.secrets.kc_realm_json.file = ../secrets/prod_kc_realm_json.age;

  active-group = {
    sp-eu = {
      enable = true;
      configFile = config.age.secrets.config_edn.path;
      proxy = {
        enable = true;
        domain = "sp-eu.ci.active-group.de";
      };
    };
    keycloak = {
      enable = true;
      realmFiles = [ config.age.secrets.kc_realm_json.path ];
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
