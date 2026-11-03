# SoildTunnel v1.1.2

A new protocol for people who run their own server, a much steadier rescue
when a connection silently dies, Tor that finally starts on busy phones, and
a faster engine underneath.

**New: TUIC**
- TUIC v5 is built in now: point the app at your own QUIC server (address,
  UUID and password) and your traffic rides modern QUIC instead of WARP.
  The credentials are sealed in the Android Keystore and passed to the core
  out of sight of the command line.
- Optional SNI override, congestion-control choice, and an "insecure" switch
  for self-signed certificates.

**Dead tunnels no longer stay dead — and no longer flap**
- The classic failure was a connection that still said Connected while no site
  would open. The watchdog now asks the engine to hop to a fresh gateway after
  the first failed check — and every protocol listens for that request,
  including WARP×2 (gool), which used to ignore it until a full engine restart.
- Hopping is bounded now: one hop per dead episode, a recovery window sized by
  the scan mode, and a cold engine restart as the last resort. Previously each
  failed check asked again, so rescans stacked on top of each other and a
  short outage turned into visible on/off flapping.
- The watchdog also checks twice as often (every 15 s), so the first rescue
  attempt lands in about 45 seconds instead of minutes.

**Tor starts again when something else holds its ports**
- On Android every app shares the same loopback, and 127.0.0.1:9050 is a
  popular address. When another app — or a leftover tor from an earlier run —
  already held it, tor died instantly with "Failed to bind one of the listener
  ports" and Tor mode never came up on any bridge. The app now claims each Tor
  port up front, moves to a free one when the preferred port is taken, and
  cleans up its own leftovers before starting.

**Speed**
- The WireGuard dataplane does less work per packet: shorter lock sections and
  no per-packet buffer copies, for higher throughput and less battery drain on
  WARP and WARP×2.

**Under the hood**
- The engine's unit tests now run on every CI build, so a release can never
  ship with a broken core.

Version: SoildTunnel 1.1.2 (version code 13).
