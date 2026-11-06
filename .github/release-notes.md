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
- WARP×2 no longer replays a dead edge. When a warp-in-warp pair went silent,
  the engine kept it — on screen and on disk — so the next reconnect (and the
  next app start) went straight back to the same blackholed edges and burned
  a full timeout there every time. Both edges are now parked for a cooldown
  and the cached pair is dropped, so every reconnect scans for a fresh one.

**Tor starts again — on every bridge**
- Two separate bugs kept Tor mode from ever coming up. The first was ports:
  on Android every app shares the same loopback, and 127.0.0.1:9050 is a
  popular address — when something else already held it, tor died instantly
  with "Failed to bind one of the listener ports" while the app logged "port
  still busy — starting anyway". The app now claims each Tor port up front,
  moves to a free one when the preferred port is taken, and cleans up its own
  leftovers before starting.
- The second was the bundled tor itself: the old 0.4.7.x daemon is no longer
  accepted by the live Tor network (its consensus now requires descriptor
  protocols it does not speak), so it refused to bootstrap on any transport,
  bridge or not, with "You should upgrade". tor-android is now 0.4.9.14,
  which the network accepts.

**Speed**
- The WireGuard dataplane does less work per packet: shorter lock sections and
  no per-packet buffer copies, for higher throughput and less battery drain on
  WARP and WARP×2.

**Under the hood**
- The engine's unit tests now run on every CI build, so a release can never
  ship with a broken core.

Version: SoildTunnel 1.1.2 (version code 13).
