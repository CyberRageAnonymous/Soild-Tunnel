# Engine Core Contract

Any core — open or proprietary external — can serve this app if it honors
this contract. That keeps a future closed-source packaging possible without
touching shared code. This repository itself never contains closed code.

## Launch

- Binary per ABI under the app's native library dir; started per connect.
- Arguments: `ConnectionProfile.toArgs()` (see `model/Profile.kt`).
- Environment: `ConnectionProfile.toEnv()`.
- Working directory: the app files directory (identity/config files live here).

## Serve

- SOCKS5 on `SOILDTUNNEL_SOCKS` (default `127.0.0.1:1819`).
- Ready means a TCP connect to that port succeeds; the app waits with
  `PortProbe` and kills the process on timeout.
- Identity/config files: `<base>-<suffix>.toml` siblings
  (`-masque`, `-gool`, `-secondary`, `-lastconn`, `-avoid`).

## Rules for new capabilities

- New behavior only behind its own CLI flags and files; never change what
  existing flags do.
- No secrets, keys, endpoints or lists in this repo; remote config or user
  input only.
- A proprietary core ships outside this repo as a separate binary honoring
  the launch/serve contract above; nothing here links against it.
