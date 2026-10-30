# SoildTunnel v1.1.2

A new protocol for people who run their own server, a much faster rescue when
a connection silently dies, and a faster engine underneath.

**New: TUIC**
- TUIC v5 is built in now: point the app at your own QUIC server (address,
  UUID and password) and your traffic rides modern QUIC instead of WARP.
  The credentials are sealed in the Android Keystore and passed to the core
  out of sight of the command line.
- Optional SNI override, congestion-control choice, and an "insecure" switch
  for self-signed certificates.

**Dead tunnels no longer stay dead**
- The classic failure was a connection that still said Connected while no site
  would open. The watchdog now asks the engine to hop to a fresh gateway after
  the FIRST failed check instead of the second — and every protocol listens
  for that request, including WARP×2 (gool), which used to ignore it until a
  full engine restart.
- The watchdog also checks twice as often, so the first rescue attempt lands
  in about 45 seconds instead of minutes. Three failed checks in a row still
  restart the engine as the last resort.

**Speed**
- The WireGuard dataplane does less work per packet: shorter lock sections and
  no per-packet buffer copies, for higher throughput and less battery drain on
  WARP and WARP×2.

**Under the hood**
- The engine's unit tests now run on every CI build, so a release can never
  ship with a broken core.

Version: SoildTunnel 1.1.2 (version code 13).
