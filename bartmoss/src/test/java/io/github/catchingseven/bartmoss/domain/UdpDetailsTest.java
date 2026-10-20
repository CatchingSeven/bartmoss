package io.github.catchingseven.bartmoss.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UdpDetailsTest {

    @Test
    void exposesUdpFieldsAndRecordValueEquality() {
        UdpDetails details = new UdpDetails(53_000, 53, 72);

        assertEquals(53_000, details.sourcePort());
        assertEquals(53, details.destionationPort());
        assertEquals(72, details.length());
        assertEquals(new UdpDetails(53_000, 53, 72), details);
        assertTrue(details instanceof ProtocolDetails);
    }
}
