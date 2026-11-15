package io.github.catchingseven.bartmoss.processing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static io.github.catchingseven.bartmoss.support.TestObjects.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.catchingseven.bartmoss.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PacketJsonConverterTest {
    private final PacketJsonConverter converter = new PacketJsonConverter();
    private final ObjectMapper parser = new ObjectMapper().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);

    @Test
    void serializesCaptureMetadataPacketsLengthsAndNanosecondTimestamp() throws Exception {
        Capture capture = capture();
        capture.getPackets().getFirst().setTimestamp(TIME.plusNanos(789));
        JsonNode json = parser.readTree(converter.convertToJson(capture));
        JsonNode packet = json.path("packets").get(0);
        assertAll(
                () -> assertEquals(1, json.path("captureId").asInt()),
                () -> assertEquals("sample.pcap", json.path("sourceFileName").asText()),
                () -> assertEquals("PCAP", json.path("fileFormat").asText()),
                () -> assertEquals(1, json.path("packets").size()),
                () -> assertEquals(0, packet.path("packetNumber").asInt()),
                () -> assertEquals(0, new BigDecimal("1794448800.123456789")
                        .compareTo(packet.path("timestamp").decimalValue())),
                () -> assertEquals(42, packet.path("capturedLength").asInt()),
                () -> assertEquals(60, packet.path("originalLength").asInt()),
                () -> assertEquals(4, packet.path("ipVersion").asInt()),
                () -> assertEquals("192.0.2.1", packet.path("sourceIpAddress").asText()),
                () -> assertEquals("198.51.100.2", packet.path("destinationIpAddress").asText()),
                () -> assertEquals(64, packet.path("ttlOrHopLimit").asInt()),
                () -> assertEquals(123, packet.path("fragementIdentification").asInt()),
                () -> assertEquals(0, packet.path("fragmentOffset").asInt()),
                () -> assertFalse(packet.path("moreFragments").asBoolean()));
    }

    @ParameterizedTest
    @EnumSource(IpProtocol.class)
    void includesProtocolSpecificRecordFields(IpProtocol protocol) throws Exception {
        Capture capture = capture();
        CapturedPacket packet = capture.getPackets().getFirst();
        packet.setIpProtocol(protocol);
        switch (protocol) {
            case TCP -> packet.setProtocolDetails(new TcpDetails(53000, 443, 4294967295L, 22,
                    Set.of(TcpFlag.SYN, TcpFlag.ACK), 65535));
            case UDP -> packet.setProtocolDetails(new UdpDetails(53000, 53, 8));
            case ICMP, ICMPv6 -> packet.setProtocolDetails(new IcmpDetails(
                    protocol == IpProtocol.ICMP ? 4 : 6, 8, 0, 123, 456));
        }
        JsonNode json = parser.readTree(converter.convertToJson(capture)).path("packets").get(0);
        JsonNode details = json.path("protocolDetails");
        assertEquals(protocol.name(), json.path("ipProtocol").asText());
        switch (protocol) {
            case TCP -> {
                assertEquals(53000, details.path("sourcePort").asInt());
                assertEquals(443, details.path("destionationPort").asInt());
                assertEquals(4294967295L, details.path("sequenceNumber").asLong());
                assertEquals(22, details.path("acknowledgementNumber").asInt());
                assertEquals(Set.of("SYN", "ACK"), Set.of(details.path("flags").get(0).asText(),
                        details.path("flags").get(1).asText()));
                assertEquals(65535, details.path("windowSize").asInt());
            }
            case UDP -> {
                assertEquals(53000, details.path("sourcePort").asInt());
                assertEquals(53, details.path("destionationPort").asInt());
                assertEquals(8, details.path("length").asInt());
            }
            case ICMP, ICMPv6 -> {
                assertEquals(protocol == IpProtocol.ICMP ? 4 : 6, details.path("version").asInt());
                assertEquals(8, details.path("type").asInt());
                assertEquals(0, details.path("code").asInt());
                assertEquals(123, details.path("identifier").asInt());
                assertEquals(456, details.path("sequenceNumber").asInt());
            }
        }
    }

    @Test
    void serializesEmptyCaptureAndNullOptionalValues() throws Exception {
        JsonNode empty = parser.readTree(converter.convertToJson(new Capture(0, "", null)));
        assertTrue(empty.path("packets").isEmpty());
        assertTrue(empty.path("analysisResults").isNull());
        assertTrue(empty.path("fileFormat").isNull());
        Capture capture = capture();
        capture.getPackets().getFirst().setProtocolDetails(null);
        assertTrue(parser.readTree(converter.convertToJson(capture))
                .path("packets").get(0).path("protocolDetails").isNull());
        assertEquals("null", converter.convertToJson(null));
    }

    @Test
    void escapesUserTextAndSerializesAnalysisResults() throws Exception {
        Capture capture = capture();
        String text = "quoted \"capture\"\npath\\file \u2603";
        capture.setSourceFileName(text);
        capture.setAnalysisResults(List.of(new AnalysisResult(2, TIME, "offline", text)));
        JsonNode json = parser.readTree(converter.convertToJson(capture));
        assertEquals(text, json.path("sourceFileName").asText());
        assertEquals(text, json.path("analysisResults").get(0).path("responseText").asText());
        assertEquals("offline", json.path("analysisResults").get(0).path("model").asText());
    }

    @Test
    void preservesPacketOrderAndIpv6NullFragmentFields() throws Exception {
        Capture capture = capture();
        CapturedPacket ipv6 = new CapturedPacket(2, TIME.minusSeconds(1), 48, 48, 6,
                "2001:db8::1", "2001:db8::2", IpProtocol.ICMPv6,
                new IcmpDetails(6, 128, 0, null, null), 255, null, null, null);
        capture.getPackets().add(ipv6);
        JsonNode packets = parser.readTree(converter.convertToJson(capture)).path("packets");
        assertEquals(0, packets.get(0).path("packetNumber").asInt());
        assertEquals(2, packets.get(1).path("packetNumber").asInt());
        assertEquals(6, packets.get(1).path("ipVersion").asInt());
        assertEquals("2001:db8::1", packets.get(1).path("sourceIpAddress").asText());
        assertTrue(packets.get(1).path("fragementIdentification").isNull());
        assertTrue(packets.get(1).path("fragmentOffset").isNull());
        assertTrue(packets.get(1).path("moreFragments").isNull());
        assertTrue(packets.get(1).path("protocolDetails").path("identifier").isNull());
        assertTrue(packets.get(1).path("protocolDetails").path("sequenceNumber").isNull());
    }

    @Test
    void reportsSerializationFailureWithOriginalCause() {
        Capture broken = mock(Capture.class);
        IllegalStateException failure = new IllegalStateException("unreadable capture");
        when(broken.getPackets()).thenThrow(failure);
        JsonProcessingException thrown = assertThrows(JsonProcessingException.class,
                () -> converter.convertToJson(broken));
        assertSame(failure, thrown.getCause());
    }
}
