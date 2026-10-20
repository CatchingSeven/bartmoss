package io.github.catchingseven.bartmoss.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProtocolDetailsTest {

    @Test
    void groupsTcpUdpAndIcmpDetailsUnderOneType() {
        ProtocolDetails tcp = new TcpDetails(12345, 443, 10L, 20L, Set.of(TcpFlag.SYN), 4096);
        ProtocolDetails udp = new UdpDetails(53000, 53, 48);
        ProtocolDetails icmp = new IcmpDetails(4, 8, 0, null, null);

        List<ProtocolDetails> details = List.of(tcp, udp, icmp);

        assertEquals(List.of(tcp, udp, icmp), details);
    }
}
