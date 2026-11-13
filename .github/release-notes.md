# SoildTunnel v1.1.4

Updates that install from inside the app, a fourth theme called Neon, and
the pinned exit country gone for good.

**Updates install from inside the app**
- When a release is out, a banner appears on the home screen: the APK
  downloads in the app with a live percentage, is checked against our own
  signature, and then goes straight to the system installer — no browser,
  no hunting for the file.
- A device with no matching asset falls back to the release page, and
  Android's install permission is asked for only when it is really
  missing instead of being assumed.

**New theme: Neon**
- A fourth choice next to System, Dark, Light and Soild Neon: champagne
  gold on warm obsidian, glass panels with a gold hairline, two slow pools
  of light behind the cards instead of a busy backdrop, and the connect
  control moved out of the scrolling column onto a glass tray at the
  bottom of the screen — a capsule that takes a different metallic fill
  for every state, with light sweeping across it.

**The pinned exit country is gone**
- Soild and Gool always took whichever inner endpoint won the walk, and
  the picker that promised a country never really kept one: the badge
  showed something other than what was picked, and a lookup that stayed
  silent counted as a pass. The picker, the engine flag and the second
  guessing are all removed — both modes take the winning endpoint again.
  Tor keeps its own exit picker.

**A flap no longer takes the whole carried hop down**
- A dead front is switched in place while the listeners keep their port
  bound, gateways race a few at a time with the rest held as standby, and
  only the inner member that died is rebuilt — so a reconnect no longer
  starts from a cold scan.
- The outer geo check now runs alongside identity loading and the inner
  handshake instead of ahead of them, so a connect does not sit for half
  a minute before the SOCKS port opens.

Everything from 1.1.3 — the WARP×2 connect-speed fix, TUIC, the Tor
upgrade and the single-engine guard — is included.

Version: SoildTunnel 1.1.4 (version code 15).
