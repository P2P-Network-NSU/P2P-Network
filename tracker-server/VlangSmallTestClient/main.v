module main

import net.http
import os
import time
import tracker

const default_tracker_url = 'https://127.0.0.1:45500' // TRACKER_URL
const default_ca_file = 'rootca.pem' // TRACKER_CA: PEM-файл с корневым сертификатом (Root CA)
const default_peer_id = '123' // PEER_ID
const default_peer_host = 'baba' // PEER_HOST
const default_peer_port = '6767' // PEER_PORT: UDP-порт HTTP/3-сервера этого клиента

fn env_or(name string, fallback string) string {
	value := os.getenv(name)
	if value == '' {
		return fallback
	}
	return value
}

fn error_name(status int) string {
	return match status {
		400 { 'INVALID_ARGUMENT (retryable=false)' }
		404 { 'NOT_FOUND (retryable=false)' }
		429, 500, 503 { 'TRACKER_UNAVAILABLE (retryable=true)' }
		else { 'неожиданный статус' }
	}
}

fn print_lease(lease tracker.PeerLeaseResponseDto) {
	if addr := lease.address {
		println('трекер зарегистрировал нас как ${addr.host}:${addr.port}')
	} else {
		println('адрес в ответе не указан')
	}
	println('интервал пульса: ${lease.heartbeat_interval_seconds} с')
	println('время жизни записи без пульса: ${lease.lease_ttl_seconds} с')
}

fn main() {
	tracker_url := env_or('TRACKER_URL', default_tracker_url)
	ca_file := env_or('TRACKER_CA', default_ca_file)
	peer_id := env_or('PEER_ID', default_peer_id)
	peer_host := env_or('PEER_HOST', default_peer_host)
	peer_port := env_or('PEER_PORT', default_peer_port).int()

	request := tracker.RegisterPeerRequestDto{
		peer_id: peer_id
		address: tracker.PeerAddressDto{
			host: peer_host
			port: i32(peer_port)
		}
	}
	body := request.encode() or {
		eprintln('не удалось закодировать запрос: ${err}')
		exit(1)
	}
	println('запрос: POST ${tracker_url}/tracker/register')
	println('тело запроса (${body.len} байт): ${body.hex()}')

	response := http.fetch(
		url:          '${tracker_url}/tracker/register'
		method:       .post
		data:         body.bytestr()
		enable_http3: true
		verify:       ca_file
		read_timeout: 5 * time.second
	) or {
		eprintln('запрос не удался: ${err}')
		eprintln('если здесь "not compiled in", запустите с флагом: v -d http3 run .')
		eprintln('сетевой сбой или таймаут трекера клиент трактует как TRACKER_UNAVAILABLE')
		exit(1)
	}

	println('HTTP-статус: ${response.status_code}')
	println('тело ответа (${response.body.len} байт): ${response.body.bytes().hex()}')

	match response.status_code {
		200 {
			lease := tracker.decode_peer_lease_response_dto(response.body.bytes()) or {
				eprintln('не удалось разобрать ответ трекера: ${err}')
				exit(1)
			}
			print_lease(lease)
		}
		else {
			println('ошибка: ${error_name(response.status_code)}')
			exit(2)
		}
	}
}
