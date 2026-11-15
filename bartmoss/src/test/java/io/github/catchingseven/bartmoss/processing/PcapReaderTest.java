package io.github.catchingseven.bartmoss.processing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static io.github.catchingseven.bartmoss.support.TestObjects.*;

import java.io.EOFException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.pcap4j.core.*;
import org.pcap4j.packet.Packet;
import io.github.catchingseven.bartmoss.domain.FileFormat;

class PcapReaderTest {
    @Test
    void characterizesNativeOpenFailureReturningDefaultCapture() throws Exception {
        try (var pcaps = mockStatic(Pcaps.class)) {
            pcaps.when(() -> Pcaps.openOffline("missing.pcap"))
                    .thenThrow(new PcapNativeException("capture missing"));
            PcapReader reader = new PcapReader();
            var capture = reader.readFromFile("missing.pcap");
            assertSame(capture, reader.getCapture());
            assertEquals(0, capture.getCaptureId());
            assertEquals("", capture.getSourceFileName());
            assertNull(capture.getFileFormat());
            assertTrue(capture.getPackets().isEmpty());
            // This describes current behavior; the integration regression requires rejection.
        }
    }

    @Test
    void leavesUnsupportedExtensionUnclassified() throws Exception {
        PcapHandle handle = mock(PcapHandle.class);
        when(handle.getNextPacketEx()).thenThrow(new EOFException());
        try (var pcaps = mockStatic(Pcaps.class)) {
            pcaps.when(() -> Pcaps.openOffline("sample.bin")).thenReturn(handle);
            assertNull(new PcapReader().readFromFile("sample.bin").getFileFormat());
            verify(handle).close();
        }
    }

    @ParameterizedTest
    @CsvSource({"sample.pcap,PCAP", "sample.PCAP,PCAP", "sample.pcapng,PCAPNG", "sample.PCAPNG,PCAPNG"})
    void detectsFormatAndReturnsEmptyCaptureAtEof(String path, FileFormat format) throws Exception {
        PcapHandle handle = mock(PcapHandle.class);
        when(handle.getNextPacketEx()).thenThrow(new EOFException());
        try (var pcaps = mockStatic(Pcaps.class)) {
            pcaps.when(() -> Pcaps.openOffline(path)).thenReturn(handle);
            PcapReader reader = new PcapReader();
            var capture = reader.readFromFile(path);
            assertSame(capture, reader.getCapture());
            assertEquals(1, capture.getCaptureId());
            assertEquals(path, capture.getSourceFileName());
            assertEquals(format, capture.getFileFormat());
            assertTrue(capture.getPackets().isEmpty());
            verify(handle).close();
        }
    }

    @Test
    void preservesOrderTimestampsAndOriginalNumberingAcrossSkippedPackets() throws Exception {
        Packet first = mock(Packet.class);
        Packet skipped = mock(Packet.class);
        Packet last = mock(Packet.class);
        when(first.length()).thenReturn(42);
        when(skipped.length()).thenReturn(14);
        when(last.length()).thenReturn(60);
        PcapHandle handle = mock(PcapHandle.class);
        when(handle.getNextPacketEx()).thenReturn(first, skipped, last).thenThrow(new EOFException());
        when(handle.getOriginalLength()).thenReturn(60, 14, 100);
        when(handle.getTimestamp()).thenReturn(Timestamp.from(TIME), Timestamp.from(TIME.minusSeconds(1)),
                Timestamp.from(TIME.minusSeconds(2)));
        var mappedFirst = capture().getPackets().getFirst();
        var mappedLast = capture().getPackets().getFirst();
        mappedLast.setPacketNumber(2);
        mappedLast.setTimestamp(TIME.minusSeconds(2));
        try (var mappers = mockConstruction(PcapMapper.class, (mapper, context) -> {
            when(mapper.mapPacket(first, TIME, 0, 42, 60)).thenReturn(Optional.of(mappedFirst));
            when(mapper.mapPacket(skipped, TIME.minusSeconds(1), 1, 14, 14)).thenReturn(Optional.empty());
            when(mapper.mapPacket(last, TIME.minusSeconds(2), 2, 60, 100)).thenReturn(Optional.of(mappedLast));
        }); var pcaps = mockStatic(Pcaps.class)) {
            pcaps.when(() -> Pcaps.openOffline("ordered.pcap")).thenReturn(handle);
            assertEquals(List.of(mappedFirst, mappedLast), new PcapReader().readFromFile("ordered.pcap").getPackets());
            var order = inOrder(mappers.constructed().getFirst(), handle);
            order.verify(mappers.constructed().getFirst()).mapPacket(first, TIME, 0, 42, 60);
            order.verify(mappers.constructed().getFirst()).mapPacket(skipped, TIME.minusSeconds(1), 1, 14, 14);
            order.verify(mappers.constructed().getFirst()).mapPacket(last, TIME.minusSeconds(2), 2, 60, 100);
            order.verify(handle).close();
        }
    }

    @Test
    void initializesMapperAndSkipsNonIpPackets() throws Exception {
        Packet packet = mock(Packet.class);
        PcapHandle handle = mock(PcapHandle.class);
        when(handle.getNextPacketEx()).thenReturn(packet).thenThrow(new EOFException());
        when(handle.getTimestamp()).thenReturn(Timestamp.from(TIME));
        try (var pcaps = mockStatic(Pcaps.class)) {
            pcaps.when(() -> Pcaps.openOffline("sample.pcap")).thenReturn(handle);
            assertTrue(new PcapReader().readFromFile("sample.pcap").getPackets().isEmpty());
            verify(handle, times(2)).getNextPacketEx();
            verify(handle).close();
        }
    }

    @Test
    void propagatesTimeoutAndClosedHandleFailuresAndClosesHandle() throws Exception {
        for (Exception failure : List.of(new TimeoutException("timeout"), new NotOpenException("closed"))) {
            PcapHandle handle = mock(PcapHandle.class);
            when(handle.getNextPacketEx()).thenThrow(failure);
            try (var pcaps = mockStatic(Pcaps.class)) {
                pcaps.when(() -> Pcaps.openOffline("sample.pcap")).thenReturn(handle);
                assertSame(failure, assertThrows(failure.getClass(), () -> new PcapReader().readFromFile("sample.pcap")));
                verify(handle).close();
            }
        }
    }

    @Test
    void closesHandleWhenMappingFails() throws Exception {
        Packet packet = mock(Packet.class);
        PcapHandle handle = mock(PcapHandle.class);
        when(handle.getNextPacketEx()).thenReturn(packet).thenThrow(new EOFException());
        when(handle.getTimestamp()).thenReturn(Timestamp.from(TIME));
        var failure = new IllegalArgumentException("invalid packet");
        try (var mappers = mockConstruction(PcapMapper.class, (mapper, context) ->
                when(mapper.mapPacket(any(), any(), anyInt(), anyInt(), anyInt())).thenThrow(failure));
                var pcaps = mockStatic(Pcaps.class)) {
            pcaps.when(() -> Pcaps.openOffline("sample.pcap")).thenReturn(handle);
            assertSame(failure, assertThrows(IllegalArgumentException.class,
                    () -> new PcapReader().readFromFile("sample.pcap")));
            verify(handle).close();
        }
    }
}
