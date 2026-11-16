module protocol

struct Header {
	magic          u16 = 0x5032
	version        u8  = 1
	msg_type       u8
	payload_length u32
}

