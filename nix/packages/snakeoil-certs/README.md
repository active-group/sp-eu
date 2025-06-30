# Snakeoil certificates

Usually used for integration testing, these certificates are created for the
domain `fake.active-group.de`. They are valid for 2 years by default.

(Re)generate them with the following command:

``` nix
nix shell .#minica \
    --ca-key ca.key.pem \
    --ca-cert ca.cert.pem \
    --domains fake.active-group.de
```
