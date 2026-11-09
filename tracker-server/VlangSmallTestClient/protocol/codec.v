module protocol

pub fn encode_message(rq REGISTER_REQ) []u8 {
	payload_buf := rq.encode()
	h := Header{ msg_type: 0x00, payload_length: payload_buf.len }

	header_buf := h.encode()

	mut buf := []u8{cap: 32}
	buf << ...header_buf
	buf << ...payload_buf

	return buf
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

fn (h Header) encode() []u8 {
	mut buf := []u8{cap: 8}

	put_u16(mut buf, h.magic)
	put_u8(mut buf, h.version)
	put_u8(mut buf, h.msg_type)
	put_u32(mut buf, h.payload_length)

	return buf
}

pub fn decode_server_answer(buf []u8) !REGISTER_RES {
	h := decode_header(buf)
	return match h.msg_type {
		1 { decode_response(buf[8..]) }
		else { error('Ошибка на сервере') }
	}
}

fn decode_response(buf []u8) REGISTER_RES {
	mut pos := 0

	host, n := get_string(buf, pos)
	pos += n

	port := get_u32(buf, pos)
	pos += 4

	heartbeat := get_u32(buf, pos)
	pos += 4

	ttl := get_u32(buf, pos)
	pos += 4

	return REGISTER_RES{
		host:                host
		port:                port
		heartbeat_intervals: heartbeat
		lease_ttl:           ttl
	}
}

fn decode_header(buf []u8) Header {
	mut pos := 0

	magic := get_u16(buf, pos)
	pos += 2

	ver := get_u8(buf, pos)
	pos += 1

	msg_t := get_u8(buf, pos)
	pos += 1

	pl_len := get_u32(buf, pos)
	pos += 4

	return Header{
		magic:          magic
		version:        ver
		msg_type:       msg_t
		payload_length: pl_len
	}
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

fn get_u8(buf []u8, pos int) u8 {
	return u8(buf[pos])
}

fn get_u16(buf []u8, pos int) u16 {
	return u16(buf[pos]) << 8 |
		u16(buf[pos + 1])
}

fn get_u32(buf []u8, pos int) u32 {
	return u32(buf[pos]) << 24 |
		u32(buf[pos + 1]) << 16 |
		u32(buf[pos + 2]) << 8 |
		u32(buf[pos + 3])
}

fn get_string(buf []u8, pos int) (string, int) {
	len := get_u16(buf, pos)
	pos_s := pos + 2
	return buf[pos_s..pos_s + len].bytestr(), int(len + 2)
}
