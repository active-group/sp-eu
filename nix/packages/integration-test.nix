{ pkgs, inputs }:

pkgs.testers.runNixOSTest {
  name = "SP-EU integration tests";

  nodes = {
    prod =
      { config, lib, ... }:
      {
        imports = [
          inputs.self.nixosModules.default
          inputs.agenix.nixosModules.default
        ];

        active-group = {
          sp-eu = {
            enable = true;
            configFile = "${../../wisen/etc/config.edn}";
            proxy.enable = false;
          };
          keycloak = {
            enable = true;
            realmFiles = [ ../nixos-configurations/keycloak-realms/SP-EU-realm.json ];
            dbPasswordFile = "${pkgs.writeText "pw" "washieristdashier"}";
            proxy.enable = false;
          };
        };

        users.users.alice = {
          isNormalUser = true;
          description = "Alice Foobar";
          password = "foobar";
          extraGroups = [ "wheel" ];
          uid = 1000;
        };

        environment.systemPackages = [
          pkgs.chromium
          pkgs.chromedriver
        ];

        networking.firewall.enable = false;

        services = {
          openssh.enable = true;
          xserver = {
            enable = true;
            displayManager = {
              lightdm.enable = true;
              autoLogin = {
                user = "alice";
                enable = true;
              };
              defaultSession = lib.mkDefault "none+icewm";
            };
            windowManager.icewm.enable = true;
          };
        };
      };
  };

  testScript = ''
    prod.wait_for_unit("keycloak")
    prod.wait_for_open_port(8080)
    prod.succeed("curl -Lf --silent http://localhost:8080")

    prod.wait_for_x()
    prod.wait_for_open_port(4321)
    prod.succeed("curl -Lf --silent http://localhost:4321")
  '';
}
