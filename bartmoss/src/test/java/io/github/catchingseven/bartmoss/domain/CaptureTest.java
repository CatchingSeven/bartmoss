package io.github.catchingseven.bartmoss.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CaptureTest {

    @Test
    void constructorInitializesPacketListAndExposesCaptureData() {
        Capture capture = new Capture(3, "sample.pcapng", FileFormat.PCAPNG);

        assertAll("constructor values",
                () -> assertEquals(3, capture.getCaptureId()),
                () -> assertEquals("sample.pcapng", capture.getSourceFileName()),
                () -> assertEquals(FileFormat.PCAPNG, capture.getFileFormat()),
                () -> assertNotNull(capture.getPackets()),
                () -> assertEquals(List.of(), capture.getPackets()));
    }

    @Test
    void settersReplaceCaptureDataAndCollections() {
        Capture capture = new Capture(3, "sample.pcap", FileFormat.PCAP);
        CapturedPacket packet = new CapturedPacket(1, 100, 64, 64, 4, "192.0.2.1", "198.51.100.2",
                IpProtocol.UDP, 64, null, null, null);
        List<CapturedPacket> packets = List.of(packet);
        AnalysisResult result = new AnalysisResult(1, Instant.parse("2026-10-20T01:02:03Z"), "model", "response");
        List<AnalysisResult> analyses = List.of(result);

        capture.setCaptureId(4);
        capture.setSourceFileName("updated.pcapng");
        capture.setFileFormat(FileFormat.PCAPNG);
        capture.setPackets(packets);
        capture.setAnalysisResults(analyses);

        assertAll("updated values",
                () -> assertEquals(4, capture.getCaptureId()),
                () -> assertEquals("updated.pcapng", capture.getSourceFileName()),
                () -> assertEquals(FileFormat.PCAPNG, capture.getFileFormat()),
                () -> assertSame(packets, capture.getPackets()),
                () -> assertSame(analyses, capture.getAnalysisResults()));
    }
}
