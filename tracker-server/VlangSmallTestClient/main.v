module main

import protocol
import net

fn main() {
	mut conn := net.dial_tcp('127.0.0.1:45500') or {
		println(err)
		exit(1)
	}
	defer {
		conn.close() or {}
	}

	rq := protocol.REGISTER_REQ{
		peer_id: '123'
		host:    'baba'
		port:    6767
	}

	frame := protocol.encode_message(rq)

	conn.write(frame) or {
		println(err)
	}

	mut buf := []u8{len: 100, cap: 200}
	n := conn.read(mut buf) or {
		println(err)
		exit(1)
	}
	data := buf[..n].clone()

	resp := protocol.decode_server_answer(data) or {
		println(err)
		exit(1)
	}

	println(resp)
}
