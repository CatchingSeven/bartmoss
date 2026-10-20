package io.github.catchingseven.bartmoss.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class TcpDetailsTest {

    @Test
    void exposesTcpFieldsAndRecordValueEquality() {
        Set<TcpFlag> flags = Set.of(TcpFlag.SYN, TcpFlag.ACK);
        TcpDetails details = new TcpDetails(51_234, 443, 100L, 200L, flags, 8_192);

        assertEquals(51_234, details.sourcePort());
        assertEquals(443, details.destionationPort());
        assertEquals(100L, details.sequenceNumber());
        assertEquals(200L, details.acknowledgementNumber());
        assertEquals(flags, details.flags());
        assertEquals(8_192, details.windowSize());
        assertEquals(new TcpDetails(51_234, 443, 100L, 200L, flags, 8_192), details);
        assertTrue(details instanceof ProtocolDetails);
    }
}
