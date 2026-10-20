package io.github.catchingseven.bartmoss.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class FileFormatTest {

    @Test
    void declaresSupportedCaptureFormats() {
        assertArrayEquals(new FileFormat[]{FileFormat.PCAP, FileFormat.PCAPNG}, FileFormat.values());
    }
}
