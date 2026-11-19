# SoildTunnel v1.1.5

Nine opt-in features for people who need to look different, not just
different: protocol chains, a client fingerprint, a stealth lab with
leak and DPI tests, a per-network firewall, scenes and a live route map.
Every one of them ships off, so a profile nobody touches connects
exactly as it did before.

**Protocol chains**
- Tor over WARP and WARP over Tor, picked as a single option. The first
  rides the engine's own SOCKS port so a bridge cannot skip the tunnel;
  the second dials the gateway through Tor and forces the MASQUE
  transport onto HTTP/2, since UDP cannot cross a Tor proxy.
- A dead hop in either direction is rebuilt by the supervisor instead
  of dropping the session.

**Client fingerprint**
- The cipher list, key groups and GREASE of the first handshake can be
  pinned to Chrome, Firefox or Safari, or picked at random, and it is
  applied to the MASQUE/H2, Soild and TUIC handshakes alike.

**Stealth lab**
- Bounded decoy traffic keeps an idle tunnel carrying ordinary-looking
  HTTPS, capped so it can never turn into a data bill.
- A cover listener answers on a chosen port with a static page instead
  of a closed port that says nobody is home.
- Separate app block lists for Wi-Fi, mobile and roaming: the firewall
  follows the network you are on.

**Leak test lab**
- One tap answers whether anything still reaches the network without
  the tunnel: exit IP, remote DNS, resolver hijack and the IPv6 path,
  each reported pass, warn or fail.

**DPI probe**
- A plain connect followed by a real TLS handshake with a chosen SNI,
  run outside the app's own VPN, so it sees exactly what the operator
  sees — open, reset, timed out or refused, per target name.

**Scenes**
- One tap to switch between Balanced, Stealth, Speed, Double Hop and
  Tor Only; each scene writes the connection fields it owns and leaves
  the rest of the profile alone.

**Live route map**
- The home screen draws the world outline with the route over it and
  the traffic flowing along it.

Everything from 1.1.4 — in-app updates, the Neon theme and the flap
fixes — is included.

Version: SoildTunnel 1.1.5 (version code 16).
