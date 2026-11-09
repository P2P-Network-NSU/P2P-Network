module protocol

pub struct REGISTER_RES {
pub:
	host                string
	port                int
	heartbeat_intervals int
	lease_ttl           int
}
