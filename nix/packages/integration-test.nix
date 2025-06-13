{ pkgs, self }:

pkgs.testers.runNixOSTest {
  name = "SP-EU integration tests";

  # FIXME(Johannes):
  # - better modularization
  # - sp-eu needs an existing working directory + (maybe default) config.edn!
  nodes.prod = {
    imports = [ self.nixosModules.default ];
    active-group = {
      sp-eu.enable = true;
      keycloak = {
        enable = true;
        env = "dev";
      };
    };
  };

  testScript = ''
    prod.wait_for_unit("multi-user.target")
    prod.wait_for_unit("keycloak")
    prod.wait_for_open_port(8080)
    prod.succeed("curl -Lf http://localhost:8080")
  '';
}
