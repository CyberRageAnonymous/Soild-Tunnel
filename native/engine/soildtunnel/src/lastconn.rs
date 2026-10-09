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
