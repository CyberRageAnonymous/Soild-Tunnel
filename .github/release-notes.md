# SoildTunnel v1.1.3

A slow-connect fix for WARP×2: one bad second no longer costs minutes.

**WARP×2 (gool) connects fast again**
- The saved pair from the last session is tried before any scan, and a
  refused handshake used to park BOTH edges for five minutes — twice the
  whole connect budget. Every scan in that run then came up with only one
  edge, the first attempt burned its full timeout, and only a second engine
  connected — to the same edges — seconds later. The usual result was two
  minutes on "Connecting" for a tunnel that was actually fine.
- A refusal that arrives in milliseconds from the recycled pair is now
  treated as a stale hint rather than a dead edge: the edges stay eligible,
  the on-disk cache is dropped and the rescan decides. A repeat failure —
  from a scan rather than the cache — parks them exactly as before.
- A scan left short of two edges by parked endpoints now retries once with
  the cooldowns ignored (warp-in-warp cannot run on a single edge anyway),
  instead of only doing that when the scan outright failed.

Everything from 1.1.2 — TUIC, the Tor upgrade, the single-engine guard and
the watchdog rework — is included.

Version: SoildTunnel 1.1.3 (version code 14).
