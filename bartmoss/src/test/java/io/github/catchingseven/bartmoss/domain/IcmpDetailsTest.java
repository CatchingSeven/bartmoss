package io.github.catchingseven.bartmoss.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IcmpDetailsTest {

    @Test
    void exposesIcmpFieldsAndAllowsAbsentOptionalFields() {
        IcmpDetails details = new IcmpDetails(4, 8, 0, null, null);

        assertEquals(4, details.version());
        assertEquals(8, details.type());
        assertEquals(0, details.code());
        assertNull(details.identifier());
        assertNull(details.sequenceNumber());
        assertTrue(details instanceof ProtocolDetails);
    }

    @Test
    void retainsOptionalIdentifierAndSequenceNumber() {
        IcmpDetails details = new IcmpDetails(6, 128, 0, 42, 9);

        assertEquals(Integer.valueOf(42), details.identifier());
        assertEquals(Integer.valueOf(9), details.sequenceNumber());
        assertEquals(new IcmpDetails(6, 128, 0, 42, 9), details);
    }
}
