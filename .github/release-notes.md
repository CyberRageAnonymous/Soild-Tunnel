# SoildTunnel v1.0.7

Stability and speed fixes for the 1.0.6 release.

**Fixes:**
- Crash on disconnect — the VPN service now promotes itself to a foreground service before handling the disconnect request, so a quick disconnect right after connect no longer kills the app with a `ForegroundServiceDidNotStartInTimeException`.
- **Tor traffic counters** — the dashboard now reads the TUN↔SOCKS bridge that Tor and per-app blocking ride on, so Downlink/Uplink finally show live numbers in Tor mode (previously stuck at 0).
- **Tor exit country** — the exit picker is locked until you connect, and Tor always starts on a Random exit like the official apps. Once the session is up, tap the pill and pick a country: the live session switches over within seconds, the choice sticks across tor restarts, and the app waits for the exit to actually report the new country instead of showing the old circuit's IP.
- **Light theme** — the home backdrop, the diagnostics log console and the power button are no longer hard-coded dark; they follow the selected theme.
- **WARP×2 country pin** — removed the GOOL double-tunnel engine from WARP×2; the profile now dials a single pinned edge so the country you pick in the node console is the edge that is actually scanned. MASQUE and WireGuard no longer show a server picker because they dial the same anycast edge.
- **WARP×2 MTU** — the Advanced MTU setting now reaches the engine (clamped to a safe 1280–1500).

**Notes:**
- Smart picks its gateway automatically, so the node console only appears for WARP×2 and as the Tor exit pill.
- Panels got a lighter liquid-glass finish and the animated neon rim around the telemetry card was retired.

Version: SoildTunnel 1.0.7 (version code 8).
