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

  virtualisation.docker.enable = true;

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

    keycloak = {
      wantedBy = [ "multi-user.target" ];
      after = [ "docker.service" ];
      description = "Start SP-EU keycloak docker container.";
      serviceConfig = {
        User = "root";
        WorkingDirectory = "/root/wisen/keycloak";
        ExecStart = ''
          ${pkgs.docker}/bin/docker run -p 8080:8080 \
            -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=agkeycloac07 \
            -e KC_PROXY_HEADERS=xforwarded \
            -e KC_HOSTNAME=https://sp-eu.ci.active-group.de/cloak \
            -e KC_HTTPS_CERTIFICATE_FILE=/opt/keycloak/data/import/fullchain.pem \
            -e KC_HTTPS_CERTIFICATE_KEY_FILE=/opt/keycloak/data/import/key.pem \
            -v /root/wisen/keycloak:/opt/keycloak/data/import \
            quay.io/keycloak/keycloak@sha256:044a457e04987e1fff756be3d2fa325a4ef420fa356b7034ecc9f1b693c32761 \
            start-dev --import-realm
        '';
      };
    };
  };

  services = {
    postgresql.enable = false;
    keycloak = {
      enable = false;

      initialAdminPassword = "agkeycloac07";

      realmFiles = [ (pkgs.writeText "speu-realm.json" "") ];

      database = {
        type = "postgresql";
        createLocally = true;

        username = "keycloak";
        passwordFile = "/root/keycloak_psql_pass";
      };

      settings = {
        hostname = "https://sp-eu.ci.active-group.de/cloak";
        http-relative-path = "/cloak";
        http-port = 38080;
        http-enabled = true;
        proxy-headers = "xforwarded";
      };
    };

    ollama = {
      enable = true;
      loadModels = [ "phi4" ];
    };

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
      # recommendedProxySettings = true;
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

  users.users.root.openssh.authorizedKeys.keys = [
    "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIMkJUKlZpli3wOU3DLI6qEm1286CFOOmwnTUWOzalmMx ci@gitlab"
  ];
  nix = {
    package = pkgs.nixVersions.stable;
    extraOptions = "experimental-features = nix-command flakes";
  };
  system.stateVersion = "24.11";
}
