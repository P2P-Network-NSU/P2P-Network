module main

import wire
import net

fn main() {
	mut conn := net.dial_tcp('127.0.0.1:45500')!
	defer {
		conn.close() or {}
	}

	rq := wire.REGISTER_REQ{
		peer_id: '123'
		host:    'baba'
		port:    6767
	}

	frame := wire.encode_message(rq)

	conn.write(frame)!

	mut buf := []u8{len: 100}
	n := conn.read(mut buf)!
	data := buf[..n].clone()

	println(data.hex())
}

// fn test_error(s string) !u16 {
// 	n := check_len(s)!
// 	return n
// }

// fn check_len(s string) !u16 {
// 	if s.len > (1 << 16) - 1 {
// 		return error('Строка слишком длинная: ${s.len} > 65535')
// 	}

// 	return u16(s.len)
// }

// fn parse_type(t u8) !string {
// 	return match t {
// 		0x01 { 'REGISTER_RES' }
// 		0x08 { error('Ошибка на сервере') }
// 		else { error('Неизвестный тип сообщения') }
// 	}
// }

// fn total_len(parts ...string) int {
// 	mut acc := 0
// 	for part in parts {
// 		acc += part.len
// 	}
// 	return acc
// }

// fn msg_name(t u8) string {
// 	return match t {
// 		0x00 { 'REGISTER_REQ' }
// 		0x01 { 'REGISTER_RES' }
// 		0x08 { 'ERROR_RES' }
// 		else { 'UNKNOWN' }
// 	}
// }

// fn byte_len(s string) u16 {
// 	return u16(s.len)
// }

// fn byte_len_rune_count(s string) (u16, u16) {
// 	return u16(s.len), u16(s.runes().len)
// }
