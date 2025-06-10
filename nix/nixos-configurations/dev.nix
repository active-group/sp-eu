{
  config,
  pkgs,
  ...
}:

{
  imports = [ ./vm-base.nix ];

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

  virtualisation.vmVariant = {
    virtualisation = {
      diskSize = 10000;
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
      ];
    };
  };
}
