package io.github.catchingseven.bartmoss.integration;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.catchingseven.bartmoss.support.TestObjects.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.catchingseven.bartmoss.processing.*;
import io.github.catchingseven.bartmoss.support.NativePcap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("integration")
@Tag("native-pcap")
class PcapJsonIntegrationTest {
    @TempDir Path temp;
    @BeforeAll static void nativeLibrary() { NativePcap.requireLibrary(); }

    @ParameterizedTest
    @ValueSource(strings = {"pcap", "pcapng"})
    void readsRealCaptureAndSerializesPacketMetadata(String extension) throws Exception {
        Path path = temp.resolve("fixture." + extension);
        if (extension.equals("pcap")) writePcap(path); else writePcapng(path);
        PcapReader reader = new PcapReader();
        var capture = reader.readFromFile(path.toString());
        var json = new ObjectMapper().readTree(new PacketJsonConverter().convertToJson(capture));
        assertEquals("fixture." + extension, json.path("sourceFileName").asText());
        assertEquals(extension.toUpperCase(java.util.Locale.ROOT), json.path("fileFormat").asText());
        assertEquals(1, json.path("packets").size());
        var packet = json.path("packets").get(0);
        assertEquals(TIME, capture.getPackets().getFirst().getTimestamp());
        assertEquals(0, packet.path("packetNumber").asInt());
        assertEquals("192.0.2.1", packet.path("sourceIpAddress").asText());
        assertEquals("198.51.100.2", packet.path("destinationIpAddress").asText());
        assertEquals("UDP", packet.path("ipProtocol").asText());
        assertEquals(42, packet.path("capturedLength").asInt());
        assertEquals(60, packet.path("originalLength").asInt());
        assertEquals(53000, packet.path("protocolDetails").path("sourcePort").asInt());
        assertEquals(53, packet.path("protocolDetails").path("destionationPort").asInt());
        // The reader must release its native handle before returning.
        Files.delete(path);
        assertFalse(Files.exists(path));
    }

    @Test
    void validHeaderOnlyCaptureSerializesEmptyPacketArray() throws Exception {
        Path path = writePcap(temp.resolve("empty.pcap"));
        Files.write(path, Arrays.copyOf(Files.readAllBytes(path), 24));
        var json = new ObjectMapper().readTree(new PacketJsonConverter()
                .convertToJson(new PcapReader().readFromFile(path.toString())));
        assertEquals("PCAP", json.path("fileFormat").asText());
        assertTrue(json.path("packets").isArray());
        assertEquals(0, json.path("packets").size());
    }
}
