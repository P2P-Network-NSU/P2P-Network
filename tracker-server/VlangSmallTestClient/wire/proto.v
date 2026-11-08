module wire

struct Header {
	magic                  u16 = 0x5032
	version                u8  = 1
	msg_type               u8
	mut     payload_length u32
}

fn (h Header) encode() []u8 {
	mut buf := []u8{cap: 8}

	put_u16(mut buf, h.magic)
	put_u8(mut buf, h.version)
	put_u8(mut buf, h.msg_type)
	put_u32(mut buf, h.payload_length)

	return buf
}

pub fn (h Header) str() string {
	return 'Header(type=${h.msg_type}, len=${h.payload_length})'
}

fn put_u8(mut buf []u8, v u8) {
	buf << v
}

fn put_u16(mut buf []u8, v u16) {
	buf << u8(v >> 8)
	buf << u8(v)
}

fn put_u32(mut buf []u8, v u32) {
	buf << u8(v >> 24)
	buf << u8(v >> 16)
	buf << u8(v >> 8)
	buf << u8(v)
}

fn put_string(mut buf []u8, s string) {
	buf << ...s.bytes()
}

pub struct REGISTER_REQ {
	pub     peer_id string
	pub     host    string
	pub     port    int
}

fn (rq REGISTER_REQ) encode() []u8 {
	mut buf := []u8{cap: 32}

	put_u16(mut buf, u16(rq.peer_id.len))
	put_string(mut buf, rq.peer_id)

	put_u16(mut buf, u16(rq.host.len))
	put_string(mut buf, rq.host)

	put_u32(mut buf, u32(rq.port))

	return buf
}

pub fn encode_message(rq REGISTER_REQ) []u8 {
	payload_buf := rq.encode()
	h := Header{ payload_length: payload_buf.len }

	header_buf := h.encode()

	mut buf := []u8{cap: 32}
	buf << ...header_buf
	buf << ...payload_buf

	return buf
}
