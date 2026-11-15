package io.github.catchingseven.bartmoss.support;

import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import com.openai.models.responses.*;
import io.github.catchingseven.bartmoss.domain.*;
import io.github.catchingseven.bartmoss.integration.OpenAIClient;

/** Test-only fixtures; no environment credentials or external requests are used. */
public final class TestObjects {
    public static final Instant TIME = Instant.parse("2026-11-12T02:00:00.123456Z");

    private TestObjects() { }

    public static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    public static Capture capture() {
        Capture capture = new Capture(1, "sample.pcap", FileFormat.PCAP);
        capture.getPackets().add(new CapturedPacket(0, TIME, 42, 60, 4,
                "192.0.2.1", "198.51.100.2", IpProtocol.UDP, new UdpDetails(53000, 53, 8),
                64, 123L, 0, false));
        return capture;
    }

    public static OpenAIClient offlineClient(com.openai.client.OpenAIClient sdk) throws Exception {
        // Constructors read credentials. A partial mock runs the real request method
        // with an injected SDK double, without constructing an HTTP client.
        OpenAIClient client = mock(OpenAIClient.class, CALLS_REAL_METHODS);
        setField(client, "client", sdk);
        return client;
    }

    public static Response response(String... texts) {
        var message = ResponseOutputMessage.builder().id("offline-message")
                .status(ResponseOutputMessage.Status.COMPLETED);
        for (String text : texts) {
            message.addContent(ResponseOutputText.builder().annotations(List.of()).text(text).build());
        }
        Response response = mock(Response.class);
        when(response.output()).thenReturn(List.of(ResponseOutputItem.ofMessage(message.build())));
        return response;
    }

    public static byte[] ethernetUdp() {
        return ByteBuffer.allocate(42).put(new byte[12]).putShort((short) 0x0800)
                .put((byte) 0x45).put((byte) 0).putShort((short) 28)
                .putShort((short) 123).putShort((short) 0).put((byte) 64).put((byte) 17)
                .putShort((short) 0).put(new byte[]{(byte) 192, 0, 2, 1, (byte) 198, 51, 100, 2})
                .putShort((short) 53000).putShort((short) 53).putShort((short) 8)
                .putShort((short) 0).array();
    }

    /** A complete, deterministic PCAP containing one synthetic Ethernet/IPv4/UDP packet. */
    public static Path writePcap(Path path) throws Exception {
        byte[] frame = ethernetUdp();
        byte[] bytes = ByteBuffer.allocate(24 + 16 + frame.length).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0xa1b2c3d4).putShort((short) 2).putShort((short) 4)
                .putInt(0).putInt(0).putInt(65535).putInt(1)
                .putInt((int) TIME.getEpochSecond()).putInt(TIME.getNano() / 1000)
                .putInt(frame.length).putInt(60).put(frame).array();
        return Files.write(path, bytes);
    }

    public static Path writePcapng(Path path) throws Exception {
        byte[] frame = ethernetUdp();
        long micros = TIME.getEpochSecond() * 1_000_000 + TIME.getNano() / 1000;
        byte[] bytes = ByteBuffer.allocate(124).order(ByteOrder.LITTLE_ENDIAN)
                // Section Header Block, Interface Description Block, Enhanced Packet Block.
                .putInt(0x0a0d0d0a).putInt(28).putInt(0x1a2b3c4d)
                .putShort((short) 1).putShort((short) 0).putLong(-1).putInt(28)
                .putInt(1).putInt(20).putShort((short) 1).putShort((short) 0)
                .putInt(65535).putInt(20)
                .putInt(6).putInt(76).putInt(0).putInt((int) (micros >>> 32)).putInt((int) micros)
                .putInt(frame.length).putInt(60).put(frame).putShort((short) 0).putInt(76).array();
        return Files.write(path, bytes);
    }
}
