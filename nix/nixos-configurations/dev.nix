{ pkgs, ... }:

{
  imports = [ ./vm-base.nix ];

  active-group.keycloak = {
    enable = true;
    realmFiles = [ ./keycloak-realms/SP-EU-realm.json ];
    dbPasswordFile = "${pkgs.writeText "db_pw" "blubberdiblub"}";
    proxy.enable = false;
  };

  users.users.alice = {
    isNormalUser = true;
    description = "Alice Foobar";
    password = "foobar";
    extraGroups = [ "wheel" ];
    uid = 1000;
  };

  networking = {
    hostName = "sp-eu-dev";
    firewall.enable = false;
  };

  virtualisation.vmVariant.virtualisation = {
    diskSize = 10000;
    memorySize = 16000;
    forwardPorts = [
      {
        from = "host";
        host.port = 2222;
        guest.port = 22;
      }
      {
        from = "host";
        host.port = 8080;
        guest.port = 8080;
      }
      {
        from = "host";
        host.port = 11434;
        guest.port = 11434;
      }
    ];
  };
}
