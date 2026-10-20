# SoildTunnel v1.1.1

The app you know is now wearing a new face, and the way it finds a working
connection got a lot sharper.

**A whole new look:**
- Every screen was redrawn around a dark aurora look: the connect button is a
  living orb with a light that sweeps around it, the background breathes behind
  your cards, and the panels are soft glass instead of flat boxes.
- The first launch now has a proper animated welcome: a spinning core, a
  strategy ladder that comes alive, and a privacy eye that closes itself.
- There is a second skin called **Soild Neon** if you like it loud — electric
  blue and red on near-black, with a grid backdrop and flat panels that light
  up at the edges.

**Smart Auto that learns your network:**
- When a strategy works, the app remembers it for that network and tries it
  first next time, so reconnects are quick instead of a fresh search every
  time.
- If your provider throttles IPv4, the tunnel now also reaches for IPv6 and
  keeps whichever path answers.
- The ladder itself is deeper for rough networks, and the heavy obfuscation
  profiles no longer sit on the TLS handshake that carries the tunnel.

**Also in this release:**
- The engine exposes its core contract, so an alternative core can be dropped
  in later without touching the app.
- Reading the logs is easier now, and the speed test and server list got the
  same treatment as the rest of the app.

Version: SoildTunnel 1.1.1 (version code 12).
