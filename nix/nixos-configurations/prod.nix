{
  config,
  pkgs,
  ...
}:

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

  security.acme = {
    acceptTerms = true;
    defaults.email = "admin@active-group.de";
  };

  systemd.services = {
    sp-eu = {
      wantedBy = [ "multi-user.target" ];
      after = [ "keycloak.service" ];
      description = "Start SP-EU backend.";
      serviceConfig = {
        User = "root";
        WorkingDirectory = "/root/wisen";
        ExecStart = "${pkgs.jdk}/bin/java -jar ${pkgs.active-group.wisen}/lib/app.jar";
        TimeoutStartSec = "0";
      };
    };
  };

  services = {
    nginx = {
      enable = true;
      virtualHosts.${domain} = {
        locations = {
          "/".proxyPass = "http://localhost:4321";
          "/cloak" = {
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
