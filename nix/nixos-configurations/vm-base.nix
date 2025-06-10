{
  config,
  pkgs,
  ...
}:

{
  virtualisation.docker.enable = true;

  systemd.services = {
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
