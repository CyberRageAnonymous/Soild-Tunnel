# SoildTunnel v1.0.5

Tor mode, light theme, and stability improvements.

**New features:**
- **Tor mode** — route the whole device through the Tor network: pick Tor as the protocol, choose Direct, Snowflake (automatic, no setup) or your own bridges, and pin an exit country. The exit can be switched live from the home screen while connected.
- **Light theme** — the app now follows the system theme, or pin Dark / Light from the new Theme card in settings.

**Notes:**
- Tor is slower than WARP by design (three hops around the world) and the first connect can take a few minutes while circuits are built.
- In Tor mode the tunnel is IPv4-only and UDP other than DNS is dropped, like other Tor VPNs.
- The app is bigger this release: it now ships the Tor daemon, Snowflake/obfs4 transports and Tor's country database.

Version: SoildTunnel 1.0.5 (version code 6).
