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
        byte[] headerBytes = new byte[MessageHeader.BYTES];
        in.readFully(headerBytes);

        ByteBuffer headerBuffer = ByteBuffer.wrap(headerBytes);

        short magic = headerBuffer.getShort();
        if (!validateProtocol(magic)) {
            throw new IOException("Неверный Magic Byte: " + magic);
        }

        byte version = headerBuffer.get();
        byte type = headerBuffer.get();
        int payloadLength = headerBuffer.getInt();
        if (!validatePayloadLength(payloadLength)) {
            throw new IOException("Недопустимая длина payload: " + payloadLength);
        }

        byte[] payload = new byte[payloadLength];
        in.readFully(payload);

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

    private boolean validateProtocol(short magic) {
        return magic == ProtocolRules.MAGIC_BYTES;
    }

    private boolean validatePayloadLength(int length) {
        return 0 <= length && length <= ProtocolRules.MAX_PAYLOAD_SIZE;
    }
}