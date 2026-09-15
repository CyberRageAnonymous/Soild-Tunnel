# SoildTunnel v1.0.6

Stability and speed fixes for the 1.0.5 release.

**Fixes:**
- Crash on disconnect — the VPN service now promotes itself to a foreground service before handling the disconnect request, so a quick disconnect right after connect no longer kills the app with a `ForegroundServiceDidNotStartInTimeException`.
- **Tor traffic counters** — the dashboard now reads the TUN↔SOCKS bridge that Tor and per-app blocking ride on, so Downlink/Uplink finally show live numbers in Tor mode (previously stuck at 0).
- **Tor exit country** — a live-switched exit country is now remembered across tor restarts (a supervisor restart used to silently revert to the old/automatic exit), and after switching, the app keeps polling the exit until the new country actually shows instead of publishing the old circuit's IP.
- **Light theme** — the home backdrop, the diagnostics log console and the power button are no longer hard-coded dark; they follow the selected theme.
- **WARP×2 (Gool) MTU** — the Advanced MTU setting now reaches the double-tunnel engine (outer/inner tunnel MTUs follow it, clamped to a safe 1280–1500). Raising the MTU on a clean network gives WARP×2 bigger TCP segments and room for full-size QUIC packets; the stock default is unchanged and stays safe on filtered networks.

**Notes:**
- WARP×2 stacks two WARP tunnels, so it inherently carries less per byte than single WARP; the MTU setting is the app-side lever for that gap.

Version: SoildTunnel 1.0.6 (version code 7).