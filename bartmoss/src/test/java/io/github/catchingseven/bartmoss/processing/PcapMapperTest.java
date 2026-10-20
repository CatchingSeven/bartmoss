package io.github.catchingseven.bartmoss.processing;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.pcap4j.packet.*;
import org.pcap4j.packet.namednumber.IpNumber;

import io.github.catchingseven.bartmoss.domain.*;

class PcapMapperTest {
    private static final Instant TIMESTAMP = Instant.parse("2026-10-20T01:02:03.123456789Z");
    private final PcapMapper mapper = new PcapMapper();

    @Test
    void ignoresNonIpPackets() {
        assertTrue(mapper.mapPacket(new UnknownPacket.Builder().rawData(new byte[]{1, 2}).build(),
                TIMESTAMP, 3, 2, 2).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mapsTcpMetadataAndAllSupportedFlags(boolean flagsSet) throws Exception {
        byte[] tcp = ByteBuffer.allocate(20)
                .putShort((short) 60000).putShort((short) 443)
                .putInt(0xFEDCBA98).putInt(0x89ABCDEF)
                .put((byte) 0x50).put((byte) (flagsSet ? 0x3F : 0))
                .putShort((short) 65000).putInt(0).array();
        CapturedPacket result = map(ipv4(6, tcp));

        assertAll(
                () -> assertEquals(7, result.getPacketNumber()),
                () -> assertEquals(TIMESTAMP, result.getTimestamp()),
                () -> assertEquals(40, result.getCapturedLength()),
                () -> assertEquals(1500, result.getOriginalLength()),
                () -> assertEquals(4, result.getIpVersion()),
                () -> assertEquals("192.0.2.1", result.getSourceIpAddress()),
                () -> assertEquals("198.51.100.2", result.getDestinationIpAddress()),
                () -> assertEquals(255, result.getTtlOrHopLimit()),
                () -> assertEquals(65535L, result.getFragementIdentification()),
                () -> assertEquals(0, result.getFragmentOffset()),
                () -> assertEquals(false, result.getMoreFragments()),
                () -> assertEquals(IpProtocol.TCP, result.getIpProtocol()));
        Set<TcpFlag> flags = flagsSet
                ? EnumSet.of(TcpFlag.SYN, TcpFlag.ACK, TcpFlag.FIN, TcpFlag.RST, TcpFlag.PSH, TcpFlag.URG)
                : Set.of();
        assertEquals(new TcpDetails(60000, 443, 0xFEDCBA98L, 0x89ABCDEFL, flags, 65000), details(result));
    }

    @Test
    void mapsUdpPortsAndUnsignedLength() throws Exception {
        byte[] udp = ByteBuffer.allocate(8).putShort((short) 53000).putShort((short) 53)
                .putShort((short) 60000).putShort((short) 0).array();
        CapturedPacket result = map(ipv4(17, udp));

        assertEquals(IpProtocol.UDP, result.getIpProtocol());
        assertEquals(new UdpDetails(53000, 53, 60000), details(result));
    }

    @Test
    void mapsIpv4FragmentMetadataForUnsupportedProtocol() throws Exception {
        IpV4Packet packet = ipv4(99, new byte[8]).getBuilder()
                .fragmentOffset((short) 123).moreFragmentFlag(true).build();
        CapturedPacket result = map(packet);

        assertEquals(65535L, result.getFragementIdentification());
        assertEquals(123, result.getFragmentOffset());
        assertEquals(true, result.getMoreFragments());
        assertNull(result.getIpProtocol());
        assertNull(details(result));
    }

    @Test
    void mapsIpv6AddressesHopLimitAndAbsentFragmentMetadata() throws Exception {
        CapturedPacket result = map(ipv6(59, new byte[0]));

        assertAll(
                () -> assertEquals(6, result.getIpVersion()),
                () -> assertEquals("2001:db8:0:0:0:0:0:1", result.getSourceIpAddress()),
                () -> assertEquals("2001:db8:0:0:0:0:0:2", result.getDestinationIpAddress()),
                () -> assertEquals(254, result.getTtlOrHopLimit()),
                () -> assertNull(result.getFragementIdentification()),
                () -> assertNull(result.getFragmentOffset()),
                () -> assertNull(result.getMoreFragments()),
                () -> assertNull(result.getIpProtocol()));
        assertNull(details(result));
    }

    @Test
    void mapsIpv6FragmentIdentificationAsUnsignedLong() throws Exception {
        byte[] fragment = ByteBuffer.allocate(16).put((byte) 59).put((byte) 0)
                .putShort((short) ((123 << 3) | 1)).putInt(0xFEDCBA98).putLong(0).array();
        CapturedPacket result = map(ipv6(44, fragment));

        assertEquals(0xFEDCBA98L, result.getFragementIdentification());
        assertEquals(123, result.getFragmentOffset());
        assertEquals(true, result.getMoreFragments());
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 0, 3})
    void extractsIpv4EchoRequestReplyAndNonEchoDetails(int type) throws Exception {
        // Exercise the existing ICMP extraction path. Real Pcap4J headers use "ICMPv4";
        // the separate characterization test below exposes the name mismatch.
        IpV4Packet packet = ipv4(1, icmp(type, type == 3 ? 1 : 0)).getBuilder()
                .protocol(new IpNumber((byte) 1, "ICMP")).build();
        CapturedPacket result = map(packet);

        assertEquals(IpProtocol.ICMP, result.getIpProtocol());
        assertEquals(new IcmpDetails(4, type, type == 3 ? 1 : 0,
                type == 3 ? null : 65534, type == 3 ? null : 32769), details(result));
    }

    @Test
    void currentlyDoesNotRecognizePcap4jIcmpv4ProtocolName() throws Exception {
        CapturedPacket result = map(ipv4(1, icmp(8, 0)));

        assertNull(result.getIpProtocol(), "Known issue: ICMPv4 does not match the mapper's ICMP name");
        assertNull(details(result));
    }

    @ParameterizedTest
    @ValueSource(ints = {128, 129, 1})
    void mapsIpv6EchoRequestReplyAndNonEchoDetails(int type) throws Exception {
        CapturedPacket result = map(ipv6(58, icmp(type, type == 1 ? 4 : 0)));

        assertEquals(IpProtocol.ICMPv6, result.getIpProtocol());
        assertEquals(new IcmpDetails(6, type, type == 1 ? 4 : 0,
                type == 1 ? null : 65534, type == 1 ? null : 32769), details(result));
    }

    @Test
    void mapsIpPacketInsideAnEthernetFrame() throws Exception {
        byte[] ip = ipv4(99, new byte[8]).getRawData();
        byte[] frame = ByteBuffer.allocate(14 + ip.length).put(new byte[12])
                .putShort((short) 0x0800).put(ip).array();
        CapturedPacket result = map(EthernetPacket.newPacket(frame, 0, frame.length));

        assertEquals(4, result.getIpVersion());
        assertEquals("192.0.2.1", result.getSourceIpAddress());
        assertEquals(frame.length, result.getCapturedLength());
    }

    @Test
    void currentlyFailsWhenTcpHeaderIsMissing() throws Exception {
        IpV4Packet packet = ipv4(6, new byte[0]);
        assertThrows(NullPointerException.class, () -> map(packet));
    }

    private CapturedPacket map(org.pcap4j.packet.Packet packet) {
        return mapper.mapPacket(packet, TIMESTAMP, 7, packet.length(), 1500).orElseThrow();
    }

    private static IpV4Packet ipv4(int protocol, byte[] payload) throws Exception {
        byte[] raw = ByteBuffer.allocate(20 + payload.length)
                .put((byte) 0x45).put((byte) 0).putShort((short) (20 + payload.length))
                .putShort((short) 0xFFFF).putShort((short) 0)
                .put((byte) 255).put((byte) protocol).putShort((short) 0)
                .put(new byte[]{(byte) 192, 0, 2, 1, (byte) 198, 51, 100, 2}).put(payload).array();
        return IpV4Packet.newPacket(raw, 0, raw.length);
    }

    private static IpV6Packet ipv6(int nextHeader, byte[] payload) throws Exception {
        byte[] source = java.net.InetAddress.getByName("2001:db8::1").getAddress();
        byte[] destination = java.net.InetAddress.getByName("2001:db8::2").getAddress();
        byte[] raw = ByteBuffer.allocate(40 + payload.length).putInt(0x60000000)
                .putShort((short) payload.length).put((byte) nextHeader).put((byte) 254)
                .put(source).put(destination).put(payload).array();
        return IpV6Packet.newPacket(raw, 0, raw.length);
    }

    private static byte[] icmp(int type, int code) {
        return ByteBuffer.allocate(8).put((byte) type).put((byte) code).putShort((short) 0)
                .putShort((short) 65534).putShort((short) 32769).array();
    }

    private static ProtocolDetails details(CapturedPacket packet) throws Exception {
        // CapturedPacket currently stores protocolDetails without exposing an accessor.
        Field field = CapturedPacket.class.getDeclaredField("protocolDetails");
        field.setAccessible(true);
        return (ProtocolDetails) field.get(packet);
    }
}
