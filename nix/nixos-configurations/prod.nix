let
  domain = "sp-eu.ci.active-group.de";
in
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
    sp-eu.enable = true;
    keycloak = {
      enable = true;
      env = "prod";
    };
  };

  security.acme = {
    acceptTerms = true;
    defaults.email = "admin@active-group.de";
  };

  services = {
    nginx = {
      enable = true;
      virtualHosts.${domain} = {
        locations = {
          "/".proxyPass = "http://localhost:4321";
          "/cloak" = {
            # FIXME(Johannes):
            # proxyPass = "http://localhost:${toString config.services.keycloak.settings.http-port}";
            proxyPass = "http://localhost:8080/";
            extraConfig = ''
              proxy_set_header    Host               $host;
              proxy_set_header    X-Real-IP          $remote_addr;
              proxy_set_header    X-Forwarded-For    $proxy_add_x_forwarded_for;
              proxy_set_header    X-Forwarded-Host   $host;
              proxy_set_header    X-Forwarded-Server $host;
              proxy_set_header    X-Forwarded-Port   $server_port;
              proxy_set_header    X-Forwarded-Proto  $scheme;
            '';
          };
        };
        enableACME = true;
        forceSSL = true;
      };
    };
  };

  users.users.root.openssh.authorizedKeys.keys = [
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIMkJUKlZpli3wOU3DLI6qEm1286CFOOmwnTUWOzalmMx ci@gitlab"
  ];

  system.stateVersion = "24.11";
}
