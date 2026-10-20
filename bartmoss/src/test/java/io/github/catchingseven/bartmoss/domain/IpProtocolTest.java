package io.github.catchingseven.bartmoss.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class IpProtocolTest {

    @Test
    void declaresSupportedIpProtocols() {
        assertArrayEquals(
                new IpProtocol[]{IpProtocol.TCP, IpProtocol.UDP, IpProtocol.ICMP, IpProtocol.ICMPv6},
                IpProtocol.values());
    }
}
