{ ... }:

let
  domain = "fake.active-group.de";
in
{
  inherit domain;
  ca = {
    cert = ./ca.cert.pem;
    key = ./ca.key.pem;
  };
  cert = ./. + "/${domain}/cert.pem";
  key = ./. + "/${domain}/key.pem";
}
