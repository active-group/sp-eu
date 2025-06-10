{
  config,
  pkgs,
  ...
}:

{
  virtualisation = {
    oci-containers.containers.kc = {
      image = "quay.io/keycloak/keycloak@sha256:044a457e04987e1fff756be3d2fa325a4ef420fa356b7034ecc9f1b693c32761";
      ports = [ "8080:8080" ];
      volumes = [
        "${../../keycloak}:/opt/keycloak/data/import"
      ];
      environment = {
        KC_BOOTSTRAP_ADMIN_USERNAME = "admin";
        KC_BOOTSTRAP_ADMIN_PASSWORD = "agkeycloac07";
        # KC_PROXY_HEADERS = "xforwarded";
        # KC_HOSTNAME = "localhost";
        # KC_HTTPS_CERTIFICATE_FILE = "/opt/keycloak/data/import/fullchain.pem";
        # KC_HTTPS_CERTIFICATE_KEY_FILE = "/opt/keycloak/data/import/key.pem";
      };
      cmd = [
        "start-dev"
        "--import-realm"
      ];
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
