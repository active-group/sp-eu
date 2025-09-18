{ config, pkgs, ... }:

{
  imports = [
    ./hardware-configuration.nix
    ../vm-base.nix
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

  environment.systemPackages = [ pkgs.gitFull ];

  age.secrets = {
    config_edn.file = ../../secrets/prod_config_edn.age;
    kc_realm_json.file = ../../secrets/prod_kc_realm_json.age;
  };

  active-group = {
    sp-eu = {
      enable = true;
      configFile = config.age.secrets.config_edn.path;
      tls = {
        domain = "sp-eu.active-group.de";
        certs = "acme";
      };
    };
    keycloak = {
      enable = true;
      realmFiles = [ config.age.secrets.kc_realm_json.path ];
      tls = {
        domain = "sp-eu.active-group.de";
        certs = "acme";
      };
    };
  };

  users.users.root.openssh.authorizedKeys.keys = [
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIMkJUKlZpli3wOU3DLI6qEm1286CFOOmwnTUWOzalmMx ci@gitlab"
    "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCnQ4OO6RiQWDYuiD01RxxPAQL6TQ64TILfBdVnvpwWo2ksUjsD5ZaAyB57AiR5NqRCtpUEuPwRsDQ8rfgZfVRiJZBs6JnCJD1yQYvIMWvuOJtDAVHAWo8XPfpS/nyog5E8Qv9u9FTKZQgw7z7ZGCDM5yG+tR94Y6g84Q4+8UW/g+HJvxX/6LKNpGdDZEkY5kty+07fltxPlozenpl7/v7F6yf2eLqa6Iy2XqhwlJeG1A/XnRKvIztvY6SUOCLQmTJm0Q4iCAoef+ub07uOZNBhDT3evsIwhqdGFUM9hXZ9lm0KloHlH2Rop4enkFfwMf0pYS3vfvLFYpfPAbmjQMFD marki"
  ];

  system.stateVersion = "24.11";
}
