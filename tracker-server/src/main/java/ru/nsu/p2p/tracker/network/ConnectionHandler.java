package ru.nsu.p2p.tracker.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import ru.nsu.p2p.common.error.ClientOperationException;
import ru.nsu.p2p.common.protocol.core.Message;
import ru.nsu.p2p.common.protocol.core.MessageHeader;
import ru.nsu.p2p.common.protocol.tracker.TrackerErrorResponse;
import ru.nsu.p2p.common.utils.MessageCodec;
import ru.nsu.p2p.common.utils.ProtocolRules;
import ru.nsu.p2p.tracker.handler.HandlerRegistry;
import ru.nsu.p2p.tracker.handler.MessageHandler;

public class ConnectionHandler implements Runnable {
    private final Socket socket;
    private final MessageCodec codec;
    private final HandlerRegistry registry;

    public ConnectionHandler(Socket socket, MessageCodec codec, HandlerRegistry registry) {
        this.socket = socket;
        this.codec = codec;
        this.registry = registry;
    }

    @Override
    public void run() {
        try (
            socket;
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream())
        ) {
            byte[] fullMessage = readMessage(in);

            Message inputMessage = codec.decode(fullMessage);
            MessageHandler<Message> messageHandler = registry.find(inputMessage.getType());
            if (messageHandler != null) {
                try {
                    Message outputMessage = messageHandler.handle(inputMessage);
                    if (outputMessage != null) {
                        byte[] outputBytes = codec.encode(outputMessage);
                        writeMessage(out, outputBytes);
                    }
                } catch (ClientOperationException e) {
                    var errorResponse = new TrackerErrorResponse(e.getError());
                    writeMessage(out, codec.encode(errorResponse));
                }
            } else {
                System.err.println("Нет обработчика для типа: " + inputMessage.getType());
            }
        } catch (IOException e) {
            System.err.println("Ошибка соединения с клиентом "
                + socket.getInetAddress() + ": " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Внутренняя ошибка сервера: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private byte[] readMessage(DataInputStream in) throws IOException {
        // защита от медленного Лориса
        socket.setSoTimeout(5_000);
        long startTime = System.currentTimeMillis();

        byte[] headerBytes = new byte[MessageHeader.BYTES];
        in.readFully(headerBytes);

        if (System.currentTimeMillis() - startTime > 30_000) {
            throw new IOException("Превышено время чтения заголовка "
                + "(Атака медленным Лорисом)");
        }

        ByteBuffer headerBuffer = ByteBuffer.wrap(headerBytes);

        short magic = headerBuffer.getShort();
        if (!validateMagic(magic)) {
            throw new IOException("Неверный Magic Byte: " + magic);
        }

        byte version = headerBuffer.get();
        if (!validateProtocolVersion(version)) {
            throw new IOException("Несовместимый по версии протокол\n"
                + "Пришедшая версия: " + version + "\nОжидаемая версия: "
                + ProtocolRules.PROTOCOL_VERSION);
        }

        byte type = headerBuffer.get();
        int payloadLength = headerBuffer.getInt();
        if (!validatePayloadLength(payloadLength)) {
            throw new IOException("Недопустимая длина payload: " + payloadLength);
        }

        byte[] payload = new byte[payloadLength];
        in.readFully(payload);

        if (System.currentTimeMillis() - startTime > 60_000) {
            throw new IOException("Превышено время чтения тела сообщения "
                + "(Атака медленным Лорисом)");
        }

        byte[] fullMessage = Arrays.copyOf(headerBytes,
            headerBytes.length + payload.length);
        System.arraycopy(payload, 0, fullMessage,
            headerBytes.length, payload.length);

        return fullMessage;
    }

    private void writeMessage(DataOutputStream out, byte[] messageBytes) throws IOException {
        out.write(messageBytes);
        out.flush();
    }

    private boolean validateMagic(short magic) {
        return magic == ProtocolRules.MAGIC_BYTES;
    }

    private boolean validateProtocolVersion(byte version) {
        return version == ProtocolRules.PROTOCOL_VERSION;
    }

    private boolean validatePayloadLength(int length) {
        return 0 <= length && length <= ProtocolRules.MAX_PAYLOAD_SIZE;
    }
}