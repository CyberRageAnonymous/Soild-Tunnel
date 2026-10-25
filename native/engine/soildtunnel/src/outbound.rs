use std::collections::HashMap;
use std::net::{IpAddr, Ipv4Addr, Ipv6Addr, SocketAddr};
use std::sync::Arc;
use std::time::{Duration, Instant};

use rand::RngCore;
use tokio::net::UdpSocket;
use tokio::sync::{mpsc, oneshot, Notify};

use crate::error::{Result, SoildTunnelError};
use crate::tls;

const TUIC_VERSION: u8 = 0x05;
const CMD_AUTHENTICATE: u8 = 0x00;
const CMD_CONNECT: u8 = 0x01;
const CMD_PACKET: u8 = 0x02;
const CMD_DISSOCIATE: u8 = 0x03;
const CMD_HEARTBEAT: u8 = 0x04;

const ADDR_NONE: u8 = 0xff;
const ADDR_NAME: u8 = 0x00;
const ADDR_V4: u8 = 0x01;
const ADDR_V6: u8 = 0x02;

const PACKET_HEADER: usize = 10;
const ASSOC_ID: u16 = 1;
const FIRST_CLIENT_UNI: u64 = 2;
const HEARTBEAT_INTERVAL: Duration = Duration::from_secs(10);
const HANDSHAKE_DEADLINE: Duration = Duration::from_secs(20);
const REPLY_DEADLINE: Duration = Duration::from_secs(30);
const BUSY_TICK: Duration = Duration::from_millis(20);
const CHANNEL_DEPTH: usize = 512;
const MAX_DGRAM_PAYLOAD: usize = 65535;

#[derive(Clone, Debug)]
pub struct TuicSettings {
    pub server: String,
    pub uuid: String,
    pub password: String,
    pub sni: String,
    pub insecure: bool,
    pub cc: String,
}

pub struct Pipe {
    to_remote: Arc<std::sync::Mutex<Option<mpsc::Sender<Vec<u8>>>>>,
    from_remote: mpsc::Receiver<Vec<u8>>,
    wake: Arc<Notify>,
}

impl Pipe {
    pub fn into_split(self) -> (PipeSender, mpsc::Receiver<Vec<u8>>) {
        (
            PipeSender {
                to_remote: self.to_remote,
                wake: self.wake,
            },
            self.from_remote,
        )
    }
}

pub struct PipeSender {
    to_remote: Arc<std::sync::Mutex<Option<mpsc::Sender<Vec<u8>>>>>,
    wake: Arc<Notify>,
}

impl PipeSender {
    pub async fn send(&self, data: Vec<u8>) -> Result<()> {
        let sender = self.to_remote.lock().ok().and_then(|guard| guard.clone());
        match sender {
            Some(tx) => tx
                .send(data)
                .await
                .map_err(|_| SoildTunnelError::Other("tuic relay closed".into()))?,
            None => return Err(SoildTunnelError::Other("tuic relay closed".into())),
        }
        self.wake.notify_one();
        Ok(())
    }

    pub async fn close(&self) {
        if let Ok(mut guard) = self.to_remote.lock() {
            *guard = None;
        }
        self.wake.notify_one();
    }
}

/// A UDP destination on the remote side. A name is kept as a name so the
/// server resolves it, which keeps the lookup off the local network.
#[derive(Clone, Debug)]
pub enum UdpTarget {
    Ip(SocketAddr),
    Name(String, u16),
}

pub struct UdpPipe {
    to_remote: Arc<std::sync::Mutex<Option<mpsc::Sender<(UdpTarget, Vec<u8>)>>>>,
    from_remote: mpsc::Receiver<(SocketAddr, Vec<u8>)>,
    wake: Arc<Notify>,
}

impl UdpPipe {
    pub async fn send_to(&self, target: UdpTarget, data: Vec<u8>) -> Result<()> {
        let sender = self.to_remote.lock().ok().and_then(|guard| guard.clone());
        match sender {
            Some(tx) => tx
                .send((target, data))
                .await
                .map_err(|_| SoildTunnelError::Other("tuic udp session closed".into()))?,
            None => {
                return Err(SoildTunnelError::Other("tuic udp session closed".into()))
            }
        }
        self.wake.notify_one();
        Ok(())
    }

    pub async fn close(&self) {
        if let Ok(mut guard) = self.to_remote.lock() {
            *guard = None;
        }
        self.wake.notify_one();
    }

    pub fn into_split(self) -> (UdpSender, mpsc::Receiver<(SocketAddr, Vec<u8>)>) {
        (
            UdpSender {
                to_remote: self.to_remote,
                wake: self.wake,
            },
            self.from_remote,
        )
    }
}

pub struct UdpSender {
    to_remote: Arc<std::sync::Mutex<Option<mpsc::Sender<(UdpTarget, Vec<u8>)>>>>,
    wake: Arc<Notify>,
}

impl UdpSender {
    pub async fn send_to(&self, target: UdpTarget, data: Vec<u8>) -> Result<()> {
        let sender = self.to_remote.lock().ok().and_then(|guard| guard.clone());
        match sender {
            Some(tx) => tx
                .send((target, data))
                .await
                .map_err(|_| SoildTunnelError::Other("tuic udp session closed".into()))?,
            None => {
                return Err(SoildTunnelError::Other("tuic udp session closed".into()))
            }
        }
        self.wake.notify_one();
        Ok(())
    }

    pub async fn close(&self) {
        if let Ok(mut guard) = self.to_remote.lock() {
            *guard = None;
        }
        self.wake.notify_one();
    }
}

enum Command {
    OpenTcp {
        host: String,
        port: u16,
        resp: oneshot::Sender<Result<Pipe>>,
    },
    OpenUdp {
        resp: oneshot::Sender<Result<UdpPipe>>,
    },
}

pub struct TuicSession {
    cmd: mpsc::Sender<Command>,
}

impl TuicSession {
    async fn open_tcp(&self, host: &str, port: u16) -> Result<Pipe> {
        let (tx, rx) = oneshot::channel();
        self.cmd
            .send(Command::OpenTcp {
                host: host.to_string(),
                port,
                resp: tx,
            })
            .await
            .map_err(|_| SoildTunnelError::Other("tuic tunnel is gone".into()))?;

        wait_for(rx).await
    }

    async fn open_udp(&self) -> Result<UdpPipe> {
        let (tx, rx) = oneshot::channel();
        self.cmd
            .send(Command::OpenUdp { resp: tx })
            .await
            .map_err(|_| SoildTunnelError::Other("tuic tunnel is gone".into()))?;

        wait_for(rx).await
    }
}

async fn wait_for<T>(rx: oneshot::Receiver<Result<T>>) -> Result<T> {
    match tokio::time::timeout(REPLY_DEADLINE, rx).await {
        Ok(Ok(result)) => result,
        Ok(Err(_)) => Err(SoildTunnelError::Other(
            "tuic tunnel dropped the request".into(),
        )),
        Err(_) => Err(SoildTunnelError::Other(
            "tuic tunnel did not answer in time".into(),
        )),
    }
}

pub enum Outbound {
    Tuic(TuicSession),
}

impl Outbound {
    pub async fn tuic(settings: TuicSettings) -> Result<Outbound> {
        let (tx, rx) = mpsc::channel::<Command>(32);
        let wake = Arc::new(Notify::new());

        tokio::spawn(async move {
            if let Err(error) = run_tuic(settings, rx, wake).await {
                log::error!("[-] tuic tunnel ended: {error}");
            }
        });

        Ok(Outbound::Tuic(TuicSession { cmd: tx }))
    }

    pub async fn open_tcp(&self, host: &str, port: u16) -> Result<Pipe> {
        match self {
            Outbound::Tuic(session) => session.open_tcp(host, port).await,
        }
    }

    pub async fn open_udp(&self) -> Result<UdpPipe> {
        match self {
            Outbound::Tuic(session) => session.open_udp().await,
        }
    }
}

pub fn split_host_port(raw: &str) -> Result<(String, u16)> {
    let trimmed = raw.trim();
    let (host, port) = trimmed
        .rsplit_once(':')
        .ok_or_else(|| SoildTunnelError::Other(format!("expected host:port, got {trimmed}")))?;
    let port: u16 = port
        .trim()
        .parse()
        .map_err(|_| SoildTunnelError::Other(format!("bad port in {trimmed}")))?;
    let host = host
        .trim()
        .trim_start_matches('[')
        .trim_end_matches(']')
        .trim()
        .to_string();
    if host.is_empty() {
        return Err(SoildTunnelError::Other(format!("missing host in {trimmed}")));
    }
    Ok((host, port))
}

async fn resolve_peer(host: &str, port: u16) -> Result<SocketAddr> {
    let mut found = tokio::net::lookup_host((host, port))
        .await
        .map_err(|e| SoildTunnelError::Other(format!("cannot resolve {host}: {e}")))?;

    found
        .next()
        .ok_or_else(|| SoildTunnelError::Other(format!("no address for {host}")))
}

fn parse_uuid(raw: &str) -> Result<[u8; 16]> {
    let trimmed = raw.trim();
    let compact: String = trimmed
        .chars()
        .filter(|c| c.is_ascii_hexdigit())
        .collect();

    if compact.len() == 32 {
        let mut out = [0u8; 16];
        for (index, chunk) in compact.as_bytes().chunks(2).enumerate() {
            let text = String::from_utf8_lossy(chunk);
            out[index] = u8::from_str_radix(&text, 16)
                .map_err(|_| SoildTunnelError::Other(format!("bad tuic uuid {trimmed}")))?;
        }
        return Ok(out);
    }

    let bytes = trimmed.as_bytes();
    if bytes.len() == 16 {
        let mut out = [0u8; 16];
        out.copy_from_slice(bytes);
        return Ok(out);
    }

    Err(SoildTunnelError::Other(format!(
        "tuic uuid must be16 bytes, got {}",
        trimmed.len()
    )))
}

fn encode_address(host: &str, port: u16) -> Result<Vec<u8>> {
    let mut out = Vec::with_capacity(host.len() + 5);

    if let Ok(v4) = host.parse::<Ipv4Addr>() {
        out.push(ADDR_V4);
        out.extend_from_slice(&v4.octets());
    } else if let Ok(v6) = host.parse::<Ipv6Addr>() {
        out.push(ADDR_V6);
        out.extend_from_slice(&v6.octets());
    } else {
        let name = host.as_bytes();
        if name.len() > 255 {
            return Err(SoildTunnelError::Other(
                "target name is too long for tuic".into(),
            ));
        }
        out.push(ADDR_NAME);
        out.push(name.len() as u8);
        out.extend_from_slice(name);
    }

    out.extend_from_slice(&port.to_be_bytes());
    Ok(out)
}

#[derive(Clone)]
enum PeerAddr {
    None,
    Ip(SocketAddr),
    Name(String, u16),
}

fn decode_address(buf: &[u8]) -> Result<(PeerAddr, usize)> {
    let kind = *buf
        .first()
        .ok_or_else(|| SoildTunnelError::Other("tuic address truncated".into()))?;

    match kind {
        ADDR_NONE => Ok((PeerAddr::None, 1)),
        ADDR_V4 => {
            if buf.len() < 7 {
                return Err(SoildTunnelError::Other("tuic ipv4 address truncated".into()));
            }
            let ip = Ipv4Addr::new(buf[1], buf[2], buf[3], buf[4]);
            let port = u16::from_be_bytes([buf[5], buf[6]]);
            Ok((
                PeerAddr::Ip(SocketAddr::new(IpAddr::V4(ip), port)),
                7,
            ))
        }
        ADDR_V6 => {
            if buf.len() < 19 {
                return Err(SoildTunnelError::Other("tuic ipv6 address truncated".into()));
            }
            let mut raw = [0u8; 16];
            raw.copy_from_slice(&buf[1..17]);
            let port = u16::from_be_bytes([buf[17], buf[18]]);
            Ok((
                PeerAddr::Ip(SocketAddr::new(IpAddr::V6(Ipv6Addr::from(raw)), port)),
                19,
            ))
        }
        ADDR_NAME => {
            let len = *buf
                .get(1)
                .ok_or_else(|| SoildTunnelError::Other("tuic name address truncated".into()))?
                as usize;
            if buf.len() < 4 + len {
                return Err(SoildTunnelError::Other("tuic name address truncated".into()));
            }
            let name = String::from_utf8_lossy(&buf[2..2 + len]).to_string();
            let port = u16::from_be_bytes([buf[2 + len], buf[3 + len]]);
            Ok((PeerAddr::Name(name, port), 4 + len))
        }
        other => Err(SoildTunnelError::Other(format!(
            "unknown tuic address type {other:#04x}"
        ))),
    }
}

struct PacketHead {
    packet: u16,
    total: u8,
    index: u8,
    size: usize,
    address: PeerAddr,
    consumed: usize,
}

fn parse_packet_head(buf: &[u8]) -> Result<PacketHead> {
    if buf.len() < PACKET_HEADER {
        return Err(SoildTunnelError::Other(
            "tuic packet header truncated".into(),
        ));
    }
    if buf[0] != TUIC_VERSION {
        return Err(SoildTunnelError::Other(format!(
            "tuic version {} is not supported",
            buf[0]
        )));
    }
    if buf[1] != CMD_PACKET {
        return Err(SoildTunnelError::Other(format!(
            "unexpected tuic command {:#04x}",
            buf[1]
        )));
    }

    let packet = u16::from_be_bytes([buf[4], buf[5]]);
    let total = buf[6];
    let index = buf[7];
    let size = u16::from_be_bytes([buf[8], buf[9]]) as usize;
    let (address, used) = decode_address(&buf[PACKET_HEADER..])?;

    Ok(PacketHead {
        packet,
        total,
        index,
        size,
        address,
        consumed: PACKET_HEADER + used,
    })
}

fn build_packet(packet: u16, index: u8, total: u8, address: &[u8], payload: &[u8]) -> Vec<u8> {
    let mut out = Vec::with_capacity(PACKET_HEADER + address.len() + payload.len());
    out.push(TUIC_VERSION);
    out.push(CMD_PACKET);
    out.extend_from_slice(&ASSOC_ID.to_be_bytes());
    out.extend_from_slice(&packet.to_be_bytes());
    out.push(total);
    out.push(index);
    out.extend_from_slice(&(payload.len() as u16).to_be_bytes());
    out.extend_from_slice(address);
    out.extend_from_slice(payload);
    out
}

struct FragmentBox {
    total: u8,
    parts: Vec<Option<Vec<u8>>>,
    filled: usize,
    address: PeerAddr,
}

struct UdpChannel {
    from_app: mpsc::Receiver<(UdpTarget, Vec<u8>)>,
    to_app: mpsc::Sender<(SocketAddr, Vec<u8>)>,
}

struct TcpSlot {
    from_app: mpsc::Receiver<Vec<u8>>,
    to_app: Option<mpsc::Sender<Vec<u8>>>,
    pending: Vec<u8>,
    fin: bool,
    fin_sent: bool,
    remote_done: bool,
}

async fn run_tuic(
    settings: TuicSettings,
    cmd_rx: mpsc::Receiver<Command>,
    wake: Arc<Notify>,
) -> Result<()> {
    let (host, port) = split_host_port(&settings.server)?;
    let peer = resolve_peer(&host, port).await?;
    let sni = if settings.sni.trim().is_empty() {
        host.clone()
    } else {
        settings.sni.trim().to_string()
    };

    let bind = if peer.is_ipv4() { "0.0.0.0:0" } else { "[::]:0" };
    let sock = UdpSocket::bind(bind).await.map_err(SoildTunnelError::Io)?;
    sock.connect(peer).await.map_err(SoildTunnelError::Io)?;
    let local = sock.local_addr().map_err(SoildTunnelError::Io)?;
    let sock = Arc::new(sock);

    let mut config = tuic_config(&sni, settings.insecure, &settings.cc)?;
    let mut raw_scid = [0u8; 16];
    rand::thread_rng().fill_bytes(&mut raw_scid);
    let scid = quiche::ConnectionId::from_ref(&raw_scid);

    let mut conn = quiche::connect(Some(&sni), &scid, local, peer, &mut config)
        .map_err(SoildTunnelError::Quic)?;

    log::info!("[+] tuic dialing {peer} with sni {sni}");

    let mut scratch = vec![0u8; MAX_DGRAM_PAYLOAD];
    let deadline = Instant::now() + HANDSHAKE_DEADLINE;

    while !conn.is_established() {
        if conn.is_closed() {
            return Err(SoildTunnelError::Other(close_reason(&conn)));
        }
        if Instant::now() >= deadline {
            return Err(SoildTunnelError::Other(format!(
                "tuic handshake with {peer} timed out"
            )));
        }

        tokio::select! {
            received = sock.recv(&mut scratch) => {
                if let Ok(len) = received {
                    let info = quiche::RecvInfo { from: peer, to: local };
                    let _ = conn.recv(&mut scratch[..len], info);
                }
            }
            _ = sleep_opt(conn.timeout()) => conn.on_timeout(),
        }

        write_out(&mut conn, &sock, &mut scratch).await;
    }

    authenticate(&mut conn, &sock, &settings, &mut scratch).await?;

    let stream_limit = conn
        .peer_transport_params()
        .map(|params| params.initial_max_streams_bidi)
        .unwrap_or(64)
        .min(4096);

    let datagram_budget = conn
        .peer_transport_params()
        .and_then(|params| params.max_datagram_frame_size)
        .unwrap_or(0);

    if datagram_budget < PACKET_HEADER as u64 + 32 {
        let _ = conn.close(true, 0x00, b"no datagrams");
        write_out(&mut conn, &sock, &mut scratch).await;
        return Err(SoildTunnelError::Other(
            "the tuic server does not offer quic datagrams".into(),
        ));
    }

    log::info!("[+] tuic session open with {peer}");

    let mut cmd_rx = cmd_rx;
    let mut slots: HashMap<u64, TcpSlot> = HashMap::new();
    let mut next_stream: u64 = 0;
    let mut next_uni: u64 = FIRST_CLIENT_UNI + 4;
    let mut udp: Option<UdpChannel> = None;
    let mut fragments: HashMap<(u16, u16), FragmentBox> = HashMap::new();
    let mut next_packet: u16 = 0;
    let mut heartbeat = tokio::time::interval(HEARTBEAT_INTERVAL);
    heartbeat.set_missed_tick_behavior(tokio::time::MissedTickBehavior::Delay);

    loop {
        let busy = slots.values().any(|slot| !slot.pending.is_empty());

        tokio::select! {
            biased;

            command = cmd_rx.recv() => {
                match command {
                    Some(Command::OpenTcp { host, port, resp }) => {
                        if slots.len() as u64 >= stream_limit {
                            let _ = resp.send(Err(SoildTunnelError::Other(
                                "too many open tuic streams".into(),
                            )));
                            continue;
                        }

                        let stream = next_stream;
                        match open_stream(&mut conn, stream, &host, port, &wake) {
                            Ok((slot, pipe)) => {
                                next_stream = stream + 4;
                                slots.insert(stream, slot);
                                let _ = resp.send(Ok(pipe));
                            }
                            Err(error) => {
                                let _ = resp.send(Err(error));
                            }
                        }
                    }
                    Some(Command::OpenUdp { resp }) => {
                        let (to_remote, from_app) = mpsc::channel(CHANNEL_DEPTH);
                        let (to_app, from_remote) = mpsc::channel(CHANNEL_DEPTH);
                        udp = Some(UdpChannel { from_app, to_app });
                        fragments.clear();
                        let pipe = UdpPipe {
                            to_remote: Arc::new(std::sync::Mutex::new(Some(to_remote))),
                            from_remote,
                            wake: wake.clone(),
                        };
                        let _ = resp.send(Ok(pipe));
                    }
                    None => break,
                }
            }

            received = sock.recv(&mut scratch) => {
                if let Ok(len) = received {
                    let info = quiche::RecvInfo { from: peer, to: local };
                    let _ = conn.recv(&mut scratch[..len], info);
                }
            }

            _ = wake.notified() => {}

            _ = sleep_opt(conn.timeout()) => conn.on_timeout(),

            _ = heartbeat.tick() => {
                if conn.is_established() {
                    let _ = conn.dgram_send(&[TUIC_VERSION, CMD_HEARTBEAT]);
                }
            }

            _ = tokio::time::sleep(BUSY_TICK), if busy => {}
        }

        pump_outbound(&mut conn, &mut slots);
        pump_inbound(&mut conn, &mut slots);
        pump_datagrams(&mut conn, &udp, &mut fragments).await;
        pump_from_app(&mut conn, &mut udp, &mut next_packet, &mut next_uni, &wake);
        write_out(&mut conn, &sock, &mut scratch).await;

        slots.retain(|_, slot| !(slot.remote_done && slot.pending.is_empty() && slot.fin_sent));

        if conn.is_closed() {
            log::warn!("[-] {}", close_reason(&conn));
            break;
        }
    }

    slots.clear();
    drop(udp);

    Ok(())
}

fn close_reason(conn: &quiche::Connection) -> String {
    if let Some(error) = conn.peer_error() {
        return format!(
            "tuic peer closed with 0x{:x}: {}",
            error.error_code,
            String::from_utf8_lossy(&error.reason)
        );
    }
    if let Some(error) = conn.local_error() {
        return format!(
            "tuic closed locally with 0x{:x}: {}",
            error.error_code,
            String::from_utf8_lossy(&error.reason)
        );
    }
    "tuic connection closed".to_string()
}

async fn authenticate(
    conn: &mut quiche::Connection,
    sock: &UdpSocket,
    settings: &TuicSettings,
    scratch: &mut [u8],
) -> Result<()> {
    let uuid = parse_uuid(&settings.uuid)?;
    let token = tls::export_keying_material(conn, &uuid, settings.password.as_bytes())?;

    let mut frame = Vec::with_capacity(50);
    frame.push(TUIC_VERSION);
    frame.push(CMD_AUTHENTICATE);
    frame.extend_from_slice(&uuid);
    frame.extend_from_slice(&token);

    write_stream(conn, FIRST_CLIENT_UNI, &frame, true)?;
    write_out(conn, sock, scratch).await;

    log::debug!("[tuic] authenticate command sent ({} bytes)", frame.len());
    Ok(())
}

fn open_stream(
    conn: &mut quiche::Connection,
    stream: u64,
    host: &str,
    port: u16,
    wake: &Arc<Notify>,
) -> Result<(TcpSlot, Pipe)> {
    let address = encode_address(host, port)?;
    let mut head = Vec::with_capacity(2 + address.len());
    head.push(TUIC_VERSION);
    head.push(CMD_CONNECT);
    head.extend_from_slice(&address);

    conn.stream_send(stream, &[], false)
        .map_err(SoildTunnelError::Quic)?;

    let (to_remote, from_app) = mpsc::channel(CHANNEL_DEPTH);
    let (to_app, from_remote) = mpsc::channel(CHANNEL_DEPTH);

    let slot = TcpSlot {
        from_app,
        to_app: Some(to_app),
        pending: head,
        fin: false,
        fin_sent: false,
        remote_done: false,
    };

    let pipe = Pipe {
        to_remote: Arc::new(std::sync::Mutex::new(Some(to_remote))),
        from_remote,
        wake: wake.clone(),
    };

    Ok((slot, pipe))
}

fn write_stream(
    conn: &mut quiche::Connection,
    stream: u64,
    data: &[u8],
    fin: bool,
) -> Result<()> {
    let mut offset = 0usize;
    let mut stalled = 0usize;
    let mut fin_sent = !fin;

    while !fin_sent {
        let last = offset >= data.len();
        match conn.stream_send(stream, &data[offset..], last) {
            Ok(written) => {
                offset += written;
                stalled = 0;
                if last {
                    fin_sent = true;
                }
            }
            Err(quiche::Error::Done) => stalled += 1,
            Err(error) => return Err(SoildTunnelError::Quic(error)),
        }

        if stalled > 32 {
            return Err(SoildTunnelError::Other(
                "tuic stream never became writable".into(),
            ));
        }
    }

    Ok(())
}

async fn write_out(conn: &mut quiche::Connection, sock: &UdpSocket, scratch: &mut [u8]) {
    loop {
        match conn.send(scratch) {
            Ok((written, _)) => {
                let _ = sock.send(&scratch[..written]).await;
            }
            Err(quiche::Error::Done) => break,
            Err(error) => {
                log::trace!("[tuic] flush: {error}");
                break;
            }
        }
    }
}

fn pump_outbound(conn: &mut quiche::Connection, slots: &mut HashMap<u64, TcpSlot>) {
    let streams: Vec<u64> = slots.keys().copied().collect();

    for stream in streams {
        let Some(slot) = slots.get_mut(&stream) else {
            continue;
        };

        loop {
            match slot.from_app.try_recv() {
                Ok(chunk) => slot.pending.extend_from_slice(&chunk),
                Err(mpsc::error::TryRecvError::Empty) => break,
                Err(mpsc::error::TryRecvError::Disconnected) => {
                    slot.fin = true;
                    break;
                }
            }
        }

        if slot.pending.is_empty() && !(slot.fin && !slot.fin_sent) {
            continue;
        }

        let capacity = conn.stream_capacity(stream).unwrap_or(0);
        if capacity == 0 && !slot.pending.is_empty() {
            continue;
        }

        let take = capacity.min(slot.pending.len());
        let fin = slot.fin && take == slot.pending.len();

        match conn.stream_send(stream, &slot.pending[..take], fin) {
            Ok(written) => {
                slot.pending.drain(..written);
                if fin {
                    slot.fin_sent = true;
                }
            }
            Err(quiche::Error::Done) => {}
            Err(error) => {
                log::debug!("[tuic] stream {stream} send failed: {error}");
                slot.pending.clear();
                slot.fin = true;
            }
        }
    }
}

fn pump_inbound(conn: &mut quiche::Connection, slots: &mut HashMap<u64, TcpSlot>) {
    let streams: Vec<u64> = slots.keys().copied().collect();

    let mut buffer = vec![0u8; 64 * 1024];

    for stream in streams {
        loop {
            let sender = match slots.get(&stream) {
                Some(slot) if !slot.remote_done => slot.to_app.clone(),
                _ => break,
            };
            let Some(sender) = sender else { break };

            let permit = match sender.try_reserve() {
                Ok(permit) => permit,
                Err(mpsc::error::TrySendError::Full(_)) => break,
                Err(mpsc::error::TrySendError::Closed(_)) => {
                    if let Some(slot) = slots.get_mut(&stream) {
                        slot.to_app = None;
                    }
                    break;
                }
            };

            match conn.stream_recv(stream, &mut buffer) {
                Ok((read, fin)) => {
                    if read > 0 {
                        permit.send(buffer[..read].to_vec());
                    }
                    if fin {
                        if let Some(slot) = slots.get_mut(&stream) {
                            slot.remote_done = true;
                            slot.to_app = None;
                        }
                        let _ = conn.stream_shutdown(stream, quiche::Shutdown::Read, 0);
                        break;
                    }
                }
                Err(quiche::Error::Done) => break,
                Err(error) => {
                    log::debug!("[tuic] stream {stream} read failed: {error}");
                    if let Some(slot) = slots.get_mut(&stream) {
                        slot.remote_done = true;
                        slot.to_app = None;
                    }
                    break;
                }
            }
        }
    }
}

async fn pump_datagrams(
    conn: &mut quiche::Connection,
    udp: &Option<UdpChannel>,
    fragments: &mut HashMap<(u16, u16), FragmentBox>,
) {
    let mut buffer = vec![0u8; MAX_DGRAM_PAYLOAD];

    loop {
        let read = match conn.dgram_recv(&mut buffer) {
            Ok(read) => read,
            Err(_) => break,
        };

        if read < PACKET_HEADER || buffer[1] != CMD_PACKET {
            continue;
        }

        let head = match parse_packet_head(&buffer[..read]) {
            Ok(head) => head,
            Err(error) => {
                log::trace!("[tuic] dropping datagram: {error}");
                continue;
            }
        };

        if head.consumed + head.size > read {
            log::trace!("[tuic] packet payload runs past the datagram");
            continue;
        }

        let payload = buffer[head.consumed..head.consumed + head.size].to_vec();
        let key = (ASSOC_ID, head.packet);

        if head.total <= 1 {
            deliver(udp, head.address, payload).await;
            continue;
        }

        let total = head.total as usize;
        if head.index as usize >= total {
            continue;
        }

        let entry = fragments.entry(key).or_insert_with(|| FragmentBox {
            total: head.total,
            parts: vec![None; total],
            filled: 0,
            address: PeerAddr::None,
        });

        if head.index == 0 {
            entry.address = head.address;
        }

        if entry.parts[head.index as usize].is_none() {
            entry.parts[head.index as usize] = Some(payload);
            entry.filled += 1;
        }

        if entry.filled == entry.total as usize {
            let mut joined = Vec::new();
            for part in &entry.parts {
                if let Some(bytes) = part {
                    joined.extend_from_slice(bytes);
                }
            }
            let address = std::mem::replace(&mut entry.address, PeerAddr::None);
            fragments.remove(&key);
            deliver(udp, address, joined).await;
        }
    }
}

fn pump_from_app(
    conn: &mut quiche::Connection,
    udp: &mut Option<UdpChannel>,
    next_packet: &mut u16,
    next_uni: &mut u64,
    wake: &Arc<Notify>,
) {
    let mut dissociate = false;

    if let Some(channel) = udp.as_mut() {
        loop {
            match channel.from_app.try_recv() {
                Ok((target, payload)) => {
                    send_udp(conn, *next_packet, &target, &payload);
                    *next_packet = next_packet.wrapping_add(1);
                }
                Err(mpsc::error::TryRecvError::Empty) => break,
                Err(mpsc::error::TryRecvError::Disconnected) => {
                    dissociate = true;
                    break;
                }
            }
        }
    }

    if dissociate {
        let mut frame = Vec::with_capacity(4);
        frame.push(TUIC_VERSION);
        frame.push(CMD_DISSOCIATE);
        frame.extend_from_slice(&ASSOC_ID.to_be_bytes());

        if let Err(error) = write_stream(conn, *next_uni, &frame, true) {
            log::debug!("[tuic] dissociate failed: {error}");
        } else {
            *next_uni = next_uni.wrapping_add(4);
            log::debug!("[tuic] udp session dissociated");
        }

        *udp = None;
        wake.notify_one();
    }
}

fn send_udp(conn: &mut quiche::Connection, packet: u16, target: &UdpTarget, payload: &[u8]) {
    let address = match encode_target(target) {
        Ok(address) => address,
        Err(error) => {
            log::trace!("[tuic] cannot encode target: {error}");
            return;
        }
    };

    let budget = conn
        .dgram_max_writable_len()
        .unwrap_or(MAX_DGRAM_PAYLOAD)
        .min(MAX_DGRAM_PAYLOAD);

    if budget <= PACKET_HEADER + address.len() {
        log::trace!("[tuic] no datagram budget right now");
        return;
    }

    let max_first = budget - PACKET_HEADER - address.len();

    if payload.len() <= max_first {
        let frame = build_packet(packet, 0, 1, &address, payload);
        if let Err(error) = conn.dgram_send(&frame) {
            log::trace!("[tuic] datagram send: {error}");
        }
        return;
    }

    let max_rest = budget - PACKET_HEADER - 1;
    if max_rest == 0 {
        return;
    }

    let extra = payload.len().saturating_sub(max_first).div_ceil(max_rest);
    let total = 1 + extra;
    if total > u8::MAX as usize {
        log::trace!("[tuic] udp payload needs too many fragments");
        return;
    }

    let mut offset = 0usize;
    for index in 0..total {
        let want = if index == 0 {
            max_first.min(payload.len() - offset)
        } else {
            max_rest.min(payload.len() - offset)
        };

        let frame_address: &[u8] = if index == 0 { &address } else { &[ADDR_NONE] };
        let frame = build_packet(
            packet,
            index as u8,
            total as u8,
            frame_address,
            &payload[offset..offset + want],
        );

        if let Err(error) = conn.dgram_send(&frame) {
            log::trace!("[tuic] datagram fragment send: {error}");
            return;
        }

        offset += want;
    }
}

fn encode_target(target: &UdpTarget) -> Result<Vec<u8>> {
    let mut out = Vec::with_capacity(20);

    match target {
        UdpTarget::Ip(addr) => {
            match addr.ip() {
                IpAddr::V4(ip) => {
                    out.push(ADDR_V4);
                    out.extend_from_slice(&ip.octets());
                }
                IpAddr::V6(ip) => {
                    out.push(ADDR_V6);
                    out.extend_from_slice(&ip.octets());
                }
            }
            out.extend_from_slice(&addr.port().to_be_bytes());
        }
        UdpTarget::Name(name, port) => {
            let raw = name.as_bytes();
            if raw.len() > 255 {
                return Err(SoildTunnelError::Other(
                    "target name is too long for tuic".into(),
                ));
            }
            out.push(ADDR_NAME);
            out.push(raw.len() as u8);
            out.extend_from_slice(raw);
            out.extend_from_slice(&port.to_be_bytes());
        }
    }

    Ok(out)
}

async fn deliver(udp: &Option<UdpChannel>, address: PeerAddr, payload: Vec<u8>) {
    let source = match address {
        PeerAddr::Ip(addr) => addr,
        PeerAddr::Name(name, port) => match tokio::net::lookup_host((name.as_str(), port)).await {
            Ok(mut found) => match found.next() {
                Some(addr) => addr,
                None => return,
            },
            Err(_) => return,
        },
        PeerAddr::None => return,
    };

    if let Some(channel) = udp {
        let _ = channel.to_app.try_send((source, payload));
    }
}

async fn sleep_opt(timeout: Option<Duration>) {
    match timeout {
        Some(duration) => tokio::time::sleep(duration).await,
        None => std::future::pending::<()>().await,
    }
}

fn tuic_config(sni: &str, insecure: bool, cc: &str) -> Result<quiche::Config> {
    use boring::ssl::{SslContextBuilder, SslMethod, SslVerifyMode, SslVersion};

    let mut builder =
        SslContextBuilder::new(SslMethod::tls()).map_err(|e| SoildTunnelError::Tls(e.to_string()))?;

    builder
        .set_min_proto_version(Some(SslVersion::TLS1_3))
        .map_err(|e| SoildTunnelError::Tls(e.to_string()))?;
    builder
        .set_max_proto_version(Some(SslVersion::TLS1_3))
        .map_err(|e| SoildTunnelError::Tls(e.to_string()))?;
    builder.set_grease_enabled(true);

    let groups = std::env::var("SOILDTUNNEL_TLS_GROUPS").ok();
    let groups = groups
        .as_deref()
        .map(str::trim)
        .filter(|value| !value.is_empty())
        .unwrap_or("P-256:X25519:P-384");
    builder
        .set_curves_list(groups)
        .map_err(|e| SoildTunnelError::Tls(e.to_string()))?;

    let alpn = std::env::var("SOILDTUNNEL_TUIC_ALPN")
        .ok()
        .map(|value| value.trim().to_string())
        .filter(|value| !value.is_empty())
        .unwrap_or_else(|| "h3".to_string());
    let mut wire = Vec::with_capacity(alpn.len() + 1);
    wire.push(alpn.len() as u8);
    wire.extend_from_slice(alpn.as_bytes());
    builder
        .set_alpn_protos(&wire)
        .map_err(|e| SoildTunnelError::Tls(e.to_string()))?;

    if insecure {
        builder.set_verify(SslVerifyMode::NONE);
        log::info!("[+] tuic accepts the server certificate as it stands");
    } else {
        crate::tls::install_webpki_verification(&mut builder, sni)?;
    }

    let mut config = quiche::Config::with_boring_ssl_ctx_builder(quiche::PROTOCOL_VERSION, builder)
        .map_err(SoildTunnelError::Quic)?;

    config
        .set_application_protos(&[alpn.as_bytes()])
        .map_err(SoildTunnelError::Quic)?;

    if !cc.trim().is_empty() {
        if let Err(error) = config.set_cc_algorithm_name(cc.trim()) {
            log::warn!("[-] tuic congestion control {cc} rejected: {error}");
        }
    }

    config.set_max_idle_timeout(60_000);
    config.set_max_recv_udp_payload_size(1350);
    config.set_max_send_udp_payload_size(1350);
    config.set_initial_max_data(10_000_000);
    config.set_initial_max_stream_data_bidi_local(2_000_000);
    config.set_initial_max_stream_data_bidi_remote(2_000_000);
    config.set_initial_max_stream_data_uni(2_000_000);
    config.set_initial_max_streams_bidi(256);
    config.set_initial_max_streams_uni(256);
    config.set_disable_active_migration(true);
    config.enable_dgram(true, 65536, 65536);

    Ok(config)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn addr_v4(host: &str, port: u16) -> Vec<u8> {
        encode_address(host, port).expect("ipv4 address")
    }

    fn addr_v6(host: &str, port: u16) -> Vec<u8> {
        encode_address(host, port).expect("ipv6 address")
    }

    #[test]
    fn an_ipv4_target_is_written_type_port_and_nothing_else() {
        let wire = addr_v4("1.2.3.4", 443);
        assert_eq!(wire, vec![0x01, 1, 2, 3, 4, 0x01, 0xbb]);
    }

    #[test]
    fn an_ipv6_target_is_written_as_sixteen_bytes_then_the_port() {
        let wire = addr_v6("2001:db8::1", 8443);
        assert_eq!(wire.len(), 19);
        assert_eq!(wire[0], ADDR_V6);
        assert_eq!(&wire[1..17], &[0x20, 0x01, 0x0d, 0xb8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1]);
        assert_eq!(&wire[17..], &[0x20, 0xfb]);
    }

    #[test]
    fn a_name_target_carries_its_length_and_still_ends_with_the_port() {
        let wire = encode_address("example.com", 80).expect("name address");
        assert_eq!(wire[0], ADDR_NAME);
        assert_eq!(wire[1] as usize, "example.com".len());
        assert_eq!(&wire[2..13], b"example.com");
        assert_eq!(&wire[13..], &[0x00, 0x50]);
    }

    #[test]
    fn the_null_address_is_a_single_byte_with_no_port() {
        let (decoded, used) = decode_address(&[ADDR_NONE]).expect("null address");
        assert_eq!(used, 1);
        assert!(matches!(decoded, PeerAddr::None));
    }

    #[test]
    fn every_address_kind_round_trips_through_decode() {
        for (host, port) in [("1.2.3.4", 443), ("2001:db8::1", 8443), ("example.com", 80)] {
            let wire = encode_address(host, port).expect("address");
            let (decoded, used) = decode_address(&wire).expect("decode");
            assert_eq!(used, wire.len(), "{host} consumed the wrong number of bytes");
            match decoded {
                PeerAddr::Ip(addr) => {
                    assert_eq!(addr.port(), port, "{host} lost its port");
                    assert_eq!(addr.ip().to_string(), host, "{host} did not survive the round trip");
                }
                PeerAddr::Name(name, addr_port) => {
                    assert_eq!(name, host);
                    assert_eq!(addr_port, port);
                }
                PeerAddr::None => panic!("{host} decoded as no address"),
            }
        }
    }

    #[test]
    fn a_truncated_address_is_reported_rather_than_read_out_of_bounds() {
        assert!(decode_address(&[]).is_err());
        assert!(decode_address(&[ADDR_V4, 1, 2]).is_err());
        assert!(decode_address(&[ADDR_V6, 1, 2, 3]).is_err());
        assert!(decode_address(&[ADDR_NAME, 5, b'a']).is_err());
        assert!(decode_address(&[0x7f]).is_err());
    }

    #[test]
    fn the_packet_head_is_ten_bytes_then_the_address_then_the_payload() {
        let address = addr_v4("1.2.3.4", 443);
        let payload = b"hello tuic";
        let frame = build_packet(7, 0, 1, &address, payload);

        assert_eq!(frame.len(), PACKET_HEADER + address.len() + payload.len());
        assert_eq!(frame[0], TUIC_VERSION);
        assert_eq!(frame[1], CMD_PACKET);
        assert_eq!(frame[2], (ASSOC_ID >> 8) as u8);
        assert_eq!(frame[3], (ASSOC_ID & 0xff) as u8);
        assert_eq!(&frame[4..6], &[0x00, 0x07]);
        assert_eq!(frame[6], 1);
        assert_eq!(frame[7], 0);
        assert_eq!(&frame[8..10], &(payload.len() as u16).to_be_bytes());

        let head = parse_packet_head(&frame).expect("packet head");
        assert_eq!(head.packet, 7);
        assert_eq!(head.total, 1);
        assert_eq!(head.index, 0);
        assert_eq!(head.size, payload.len());
        assert_eq!(head.consumed, PACKET_HEADER + address.len());
        assert_eq!(&frame[head.consumed..head.consumed + head.size], payload);
    }

    #[test]
    fn a_later_fragment_carries_the_null_address() {
        let payload = vec![0u8; 40];
        let frame = build_packet(9, 2, 3, &[ADDR_NONE], &payload);

        let head = parse_packet_head(&frame).expect("packet head");
        assert_eq!(head.index, 2);
        assert_eq!(head.total, 3);
        assert_eq!(head.consumed, PACKET_HEADER + 1);
        assert!(matches!(head.address, PeerAddr::None));
        assert_eq!(head.size, payload.len());
    }

    #[test]
    fn a_packet_head_is_rejected_when_the_version_or_command_is_wrong() {
        let mut frame = build_packet(1, 0, 1, &[ADDR_NONE], b"x");
        frame[0] = 0x04;
        assert!(parse_packet_head(&frame).is_err());

        let mut frame = build_packet(1, 0, 1, &[ADDR_NONE], b"x");
        frame[1] = CMD_HEARTBEAT;
        assert!(parse_packet_head(&frame).is_err());

        assert!(parse_packet_head(&[0x05, CMD_PACKET]).is_err());
    }

    #[test]
    fn the_payload_length_is_read_from_the_head_and_not_from_the_datagram() {
        let frame = build_packet(1, 0, 1, &[ADDR_NONE], b"12345");
        let head = parse_packet_head(&frame).expect("packet head");
        assert_eq!(head.size, 5);
        assert_eq!(frame.len(), head.consumed + head.size);

        let mut lying = frame.clone();
        lying[9] = 0x40;
        let head = parse_packet_head(&lying).expect("packet head");
        assert_eq!(head.size, 64);
        assert!(head.consumed + head.size > lying.len());
    }

    #[test]
    fn an_association_id_is_accepted_with_or_without_the_expected_value() {
        let mut frame = build_packet(1, 0, 1, &[ADDR_NONE], b"x");
        frame[2] = 0x12;
        frame[3] = 0x34;
        assert!(parse_packet_head(&frame).is_ok());
    }

    #[test]
    fn a_uuid_may_be_written_with_or_without_dashes() {
        let dashed = "123e4567-e89b-12d3-a456-426614174000";
        let bare: String = dashed.chars().filter(|c| *c != '-').collect();

        let from_dashed = parse_uuid(dashed).expect("dashed uuid");
        let from_bare = parse_uuid(&bare).expect("bare uuid");
        assert_eq!(from_dashed, from_bare);
        assert_eq!(from_dashed[0], 0x12);
        assert_eq!(from_dashed[1], 0x3e);
        assert_eq!(from_dashed[15], 0x00);
    }

    #[test]
    fn a_malformed_uuid_is_reported_instead_of_being_padded() {
        assert!(parse_uuid("").is_err());
        assert!(parse_uuid("not-a-uuid").is_err());
        assert!(parse_uuid("1234").is_err());
        assert!(parse_uuid(&"z".repeat(32)).is_err());
    }

    #[test]
    fn a_host_and_port_split_on_the_last_colon_only() {
        assert_eq!(split_host_port("example.com:443").expect("split"), ("example.com".to_string(), 443));
        assert_eq!(split_host_port("1.2.3.4:8443").expect("split"), ("1.2.3.4".to_string(), 8443));
        assert_eq!(split_host_port("[2001:db8::1]:443").expect("split"), ("2001:db8::1".to_string(), 443));
        assert!(split_host_port("example.com").is_err());
        assert!(split_host_port("example.com:").is_err());
        assert!(split_host_port(":443").is_err());
        assert!(split_host_port("example.com:notaport").is_err());
    }

    #[test]
    fn a_udp_target_keeps_a_name_as_a_name() {
        let wire = encode_target(&UdpTarget::Name("example.com".into(), 53)).expect("name target");
        assert_eq!(wire[0], ADDR_NAME);
        assert_eq!(&wire[2..13], b"example.com");
        assert_eq!(&wire[13..], &[0x00, 0x35]);

        let ip = UdpTarget::Ip("1.2.3.4:53".parse().expect("socket address"));
        let wire = encode_target(&ip).expect("ip target");
        assert_eq!(wire, addr_v4("1.2.3.4", 53));
    }
}
