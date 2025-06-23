let
  schlegel = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCnQ4OO6RiQWDYuiD01RxxPAQL6TQ64TILfBdVnvpwWo2ksUjsD5ZaAyB57AiR5NqRCtpUEuPwRsDQ8rfgZfVRiJZBs6JnCJD1yQYvIMWvuOJtDAVHAWo8XPfpS/nyog5E8Qv9u9FTKZQgw7z7ZGCDM5yG+tR94Y6g84Q4+8UW/g+HJvxX/6LKNpGdDZEkY5kty+07fltxPlozenpl7/v7F6yf2eLqa6Iy2XqhwlJeG1A/XnRKvIztvY6SUOCLQmTJm0Q4iCAoef+ub07uOZNBhDT3evsIwhqdGFUM9hXZ9lm0KloHlH2Rop4enkFfwMf0pYS3vfvLFYpfPAbmjQMFD markusschlegel@MacBook-Air-4.local";
  maier = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAICR48mD5nj7LpDcrpN1nX2bWghMQvnt9IWGjAWk54dWN johannes.maier@mailbox.org";
  leitz = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIG6o4SiFpsgaR5H7MH2slofck0Mn8rveACBMPqKi/N4k leitz@leitz";
  schlotterbeck = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCuDtoiZoRtmRHRvhH8Lmf0WJFp2ewBnk5/5SwJl6q2Fs3oEjPwHTppFFE6yMMIfFwVZmcwl4jEPamcf0EKlqILG2rYAjCpUCe3ABOTOnNeA048YOABcXDyPYtP4llbWmtL7I24ArOieEAhFMGCmHtE7w049JGiHLCkW52fPIuSTs/hn2bjcpEFS7w+ZI1xb+8f6zbIl2y3e5r3pJfg/tu1gPL+gwQN7YD48BBK9de4c8yCF9rrOhsQU7iYohSsKkzkVHHNHL8h9HtKX4mVUYN9OMEx3LGfnvmp5PEhpbi6PqUB6HhkgzO1NdWSVT9wxYudHAeSNGSHJt/hW9i/xLXp schlotterbeck@Admins-MBP.home.active-group.de";
  prod = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIO0L3uAkiMmNXbgOgJDVTgfXEVgTt39dzFp95KUL+QwL root@sp-eu";
  all = [
    schlegel
    maier
    leitz
    schlotterbeck
    prod
  ];
in
{
  "keycloak_db.age".publicKeys = all;
}
