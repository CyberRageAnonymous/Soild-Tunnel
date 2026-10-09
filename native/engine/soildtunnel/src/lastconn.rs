use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
pub struct LastConnection {
    pub peer: String,
    #[serde(default)]
    pub profile: String,
}

pub fn load(path: &str) -> Option<LastConnection> {
    let text = std::fs::read_to_string(path).ok()?;
    toml::from_str(&text).ok()
}

pub fn save(path: &str, peer: &str, profile: &str) {
    let conn = LastConnection {
        peer: peer.to_string(),
        profile: profile.to_string(),
    };
    match toml::to_string_pretty(&conn) {
        Ok(text) => {
            if let Err(e) = std::fs::write(path, text) {
                log::debug!("[lastconn] failed to save {path}: {e}");
            }
        }
        Err(e) => log::debug!("[lastconn] failed to encode: {e}"),
    }
}

/// Drop the cached gateway when it is known bad (e.g. iranian egress), so
/// the next loop scans fresh instead of reusing it.
pub fn forget(path: &str, peer: &str) {
    match load(path) {
        Some(cached) if cached.peer == peer => {
            if let Err(e) = std::fs::remove_file(path) {
                log::debug!("[lastconn] failed to forget {path}: {e}");
            } else {
                log::info!("[+] forgot cached gateway {peer}");
            }
        }
        _ => {}
    }
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
struct AvoidEntry {
    peer: String,
    at: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
struct AvoidList {
    #[serde(default)]
    entries: Vec<AvoidEntry>,
}

const AVOID_TTL_SECS: u64 = 12 * 3600;

fn now_secs() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

/// Gateways avoided earlier (e.g. iranian egress), surviving restarts.
/// Entries older than the TTL are ignored so a gateway can be retried later.
pub fn load_avoided(path: &str) -> std::collections::HashSet<std::net::SocketAddr> {
    let now = now_secs();
    let text = std::fs::read_to_string(path).unwrap_or_default();
    let list: AvoidList = toml::from_str(&text).unwrap_or_default();
    list.entries
        .into_iter()
        .filter(|e| now.saturating_sub(e.at) < AVOID_TTL_SECS)
        .filter_map(|e| e.peer.parse().ok())
        .collect()
}

pub fn save_avoided(path: &str, peers: &std::collections::HashSet<std::net::SocketAddr>) {
    let now = now_secs();
    let text = std::fs::read_to_string(path).unwrap_or_default();
    let mut list: AvoidList = toml::from_str(&text).unwrap_or_default();
    list.entries.retain(|e| now.saturating_sub(e.at) < AVOID_TTL_SECS);
    for peer in peers {
        let peer = peer.to_string();
        if !list.entries.iter().any(|e| e.peer == peer) {
            list.entries.push(AvoidEntry { peer, at: now });
        }
    }
    match toml::to_string_pretty(&list) {
        Ok(text) => {
            if let Err(e) = std::fs::write(path, text) {
                log::debug!("[lastconn] failed to save avoided list {path}: {e}");
            }
        }
        Err(e) => log::debug!("[lastconn] failed to encode avoided list: {e}"),
    }
}
