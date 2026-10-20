package io.github.catchingseven.bartmoss.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PacketTest {

    @Test
    void constructorAndSettersExposePacketMetadata() {
        Instant timestamp = Instant.parse("2026-10-20T01:02:03.123456789Z");
        TcpDetails details = new TcpDetails(12345, 443, 1L, 2L, java.util.Set.of(TcpFlag.SYN), 8192);
        CapturedPacket packet = new CapturedPacket(12, timestamp, 128, 140, 4,
                "192.0.2.10", "198.51.100.20", IpProtocol.TCP,
                details, 63, 65_000L, 2, true);

        assertAll("constructor values",
                () -> assertEquals(12, packet.getPacketNumber()),
                () -> assertEquals(timestamp, packet.getTimestamp()),
                () -> assertEquals(128, packet.getCapturedLength()),
                () -> assertEquals(140, packet.getOriginalLength()),
                () -> assertEquals(4, packet.getIpVersion()),
                () -> assertEquals("192.0.2.10", packet.getSourceIpAddress()),
                () -> assertEquals("198.51.100.20", packet.getDestinationIpAddress()),
                () -> assertEquals(IpProtocol.TCP, packet.getIpProtocol()),
                () -> assertEquals(63, packet.getTtlOrHopLimit()),
                () -> assertEquals(65_000L, packet.getFragementIdentification().longValue()),
                () -> assertEquals(2, packet.getFragmentOffset().intValue()),
                () -> assertEquals(Boolean.TRUE, packet.getMoreFragments()));

        packet.setPacketNumber(13);
        Instant updatedTimestamp = timestamp.plusNanos(1);
        packet.setTimestamp(updatedTimestamp);
        packet.setCapturedLength(96);
        packet.setOriginalLength(100);
        packet.setIpVersion(6);
        packet.setSourceIpAddress("2001:db8::1");
        packet.setDestinationIpAddress("2001:db8::2");
        packet.setIpProtocol(IpProtocol.UDP);
        packet.setTtlOrHopLimit(55);
        packet.setFragementIdentification(70_000L);
        packet.setFragmentOffset(0);
        packet.setMoreFragments(false);

        assertAll("updated values",
                () -> assertEquals(13, packet.getPacketNumber()),
                () -> assertEquals(updatedTimestamp, packet.getTimestamp()),
                () -> assertEquals(96, packet.getCapturedLength()),
                () -> assertEquals(100, packet.getOriginalLength()),
                () -> assertEquals(6, packet.getIpVersion()),
                () -> assertEquals("2001:db8::1", packet.getSourceIpAddress()),
                () -> assertEquals("2001:db8::2", packet.getDestinationIpAddress()),
                () -> assertEquals(IpProtocol.UDP, packet.getIpProtocol()),
                () -> assertEquals(55, packet.getTtlOrHopLimit()),
                () -> assertEquals(70_000L, packet.getFragementIdentification().longValue()),
                () -> assertEquals(0, packet.getFragmentOffset().intValue()),
                () -> assertFalse(packet.getMoreFragments()));
    }

    @Test
    void permitsAbsentFragmentMetadata() {
        CapturedPacket packet = new CapturedPacket(1, Instant.EPOCH, 60, 60, 4,
                "192.0.2.1", "198.51.100.1", IpProtocol.UDP, new UdpDetails(53, 53000, 40), 64,
                null, null, null);

        assertNull(packet.getFragementIdentification());
        assertNull(packet.getFragmentOffset());
        assertNull(packet.getMoreFragments());

        packet.setFragementIdentification(null);
        packet.setFragmentOffset(null);
        packet.setMoreFragments(null);

        assertAll("nullable fragment metadata remains absent",
                () -> assertNull(packet.getFragementIdentification()),
                () -> assertNull(packet.getFragmentOffset()),
                () -> assertNull(packet.getMoreFragments()));
    }
}
