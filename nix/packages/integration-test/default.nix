{
  inputs,
  lib,
  testers,
  active-group,
}:

let
  certs = active-group.snakeoil-certs;
in
testers.runNixOSTest (
  { config, ... }:
  {
    name = "SP-EU integration tests";

    nodes = {
      prod =
        { pkgs, ... }:
        {
          imports = [
            inputs.self.nixosModules.default
            inputs.agenix.nixosModules.default
          ];

          virtualisation = {
            diskSize = 15000;
            memorySize = 4096;
            cores = 2;
          };

          active-group = {
            sp-eu = {
              enable = true;
              configFile = "${./config.edn}";
              tls = {
                inherit (certs) domain;
                certs = {
                  inherit (certs) cert key;
                };
              };
            };
            keycloak = {
              enable = true;
              # TODO(Johannes): patch this instead somehow? needs option adaptation
              realmFiles = [ ./SP-EU-realm-tls.json ];
              dbPasswordFile = "${pkgs.writeText "pw" "washieristdashier"}";
              tls = {
                inherit (certs) domain;
                certs = {
                  inherit (certs) cert key;
                };
              };
            };
          };

          services.openssh.enable = true;

          networking = {
            firewall.enable = false;
            extraHosts = ''
              127.0.0.1 prod
              127.0.0.1 ${certs.domain}
            '';
          };

          security.pki.certificateFiles = [
            certs.ca.cert
          ];
        };

      client =
        { nodes, pkgs, ... }:
        {
          users.users.alice = {
            isNormalUser = true;
            description = "Alice Foobar";
            password = "foobar";
            extraGroups = [ "wheel" ];
            uid = 1000;
          };

          security.pki.certificateFiles = [
            certs.ca.cert
          ];

          environment = {
            systemPackages = [
              pkgs.chromium
              pkgs.chromedriver
            ];
            variables."XAUTHORITY" = "/home/alice/.Xauthority";
          };

          networking.extraHosts = "${nodes.prod.networking.primaryIPAddress} ${certs.domain}";

          virtualisation = {
            memorySize = 2048;
            cores = 2;
          };

          services = {
            openssh.enable = true;
            displayManager = {
              autoLogin = {
                user = "alice";
                enable = true;
              };
              defaultSession = lib.mkDefault "none+icewm";
            };
            xserver = {
              enable = true;
              displayManager = {
                lightdm.enable = true;
              };
              windowManager.icewm.enable = true;
            };
          };
        };
    };

    testScript = ''
      import shlex

      def as_user(cmd):
          return "su - alice -c " + shlex.quote(cmd)

      start_all()

      prod.wait_for_unit("keycloak")
      prod.wait_for_open_port(8080)
      prod.succeed("curl -Lf --silent http://localhost:8080")
      prod.wait_for_open_port(4321)
      prod.succeed("curl -Lf --silent http://localhost:4321")

      client.wait_for_x()
      client.succeed("curl -Lf --silent https://${certs.domain}")
      client.succeed(as_user("${lib.getExe config.node.pkgs.active-group.e2e-test} http://${certs.domain}"))
    '';
  }
)
