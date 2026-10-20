package io.github.catchingseven.bartmoss.processing;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.Packet;

import io.github.catchingseven.bartmoss.domain.Capture;
import io.github.catchingseven.bartmoss.domain.CapturedPacket;
import io.github.catchingseven.bartmoss.domain.FileFormat;
import io.github.catchingseven.bartmoss.domain.IpProtocol;
import io.github.catchingseven.bartmoss.domain.UdpDetails;

class PcapReaderTest {
    @Test
    void returnsDefaultCaptureAndReportsOpenFailure() throws Exception {
        String path = "missing.pcapng";
        try (MockedStatic<Pcaps> pcaps = mockStatic(Pcaps.class)) {
            pcaps.when(() -> Pcaps.openOffline(path)).thenThrow(new PcapNativeException("invalid capture"));
            ByteArrayOutputStream errors = new ByteArrayOutputStream();
            PrintStream previous = System.err;
            Capture capture;
            try (PrintStream redirected = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
                System.setErr(redirected);
                capture = new PcapReader().readFromFile(path);
            } finally {
                System.setErr(previous);
            }

            assertAll(
                    () -> assertEquals(0, capture.getCaptureId()),
                    () -> assertEquals("", capture.getSourceFileName()),
                    () -> assertNull(capture.getFileFormat()),
                    () -> assertTrue(capture.getPackets().isEmpty()),
                    () -> assertEquals("Unable to open capture: invalid capture" + System.lineSeparator(),
                            errors.toString(StandardCharsets.UTF_8)));
            pcaps.verify(() -> Pcaps.openOffline(path));
        }
    }

    @Test
    void preservesOrderTimestampsLengthsAndNumberingAcrossSkippedPackets() throws Exception {
        String path = Path.of("captures", "ordered.PCAPNG").toString();
        Instant firstTime = Instant.parse("2026-10-20T01:02:03.123456789Z");
        Instant skippedTime = firstTime.minusSeconds(1);
        Instant lastTime = firstTime.minusSeconds(2);
        Packet first = mock(Packet.class);
        Packet skipped = mock(Packet.class);
        Packet last = mock(Packet.class);
        when(first.length()).thenReturn(60);
        when(skipped.length()).thenReturn(14);
        when(last.length()).thenReturn(80);
        PcapHandle handle = mock(PcapHandle.class);
        when(handle.getNextPacketEx()).thenReturn(first, skipped, last)
                .thenThrow(new PcapNativeException("read failed"));
        when(handle.getOriginalLength()).thenReturn(100, 14, 120);
        when(handle.getTimestamp()).thenReturn(Timestamp.from(firstTime), Timestamp.from(skippedTime),
                Timestamp.from(lastTime));
        PcapMapper mapper = mock(PcapMapper.class);
        CapturedPacket firstMapped = packet(0, firstTime, 60, 100);
        CapturedPacket lastMapped = packet(2, lastTime, 80, 120);
        when(mapper.mapPacket(first, firstTime, 0, 60, 100)).thenReturn(Optional.of(firstMapped));
        when(mapper.mapPacket(skipped, skippedTime, 1, 14, 14)).thenReturn(Optional.empty());
        when(mapper.mapPacket(last, lastTime, 2, 80, 120)).thenReturn(Optional.of(lastMapped));
        PcapReader reader = readerWith(mapper);

        try (MockedStatic<Pcaps> pcaps = mockStatic(Pcaps.class)) {
            pcaps.when(() -> Pcaps.openOffline(path)).thenReturn(handle);
            // A read error currently returns partial results; EOF does not return them.
            Capture capture = reader.readFromFile(path);

            assertEquals(1, capture.getCaptureId());
            assertEquals("ordered.PCAPNG", capture.getSourceFileName());
            assertEquals(FileFormat.PCAPNG, capture.getFileFormat());
            assertEquals(List.of(firstMapped, lastMapped), capture.getPackets());
            var order = inOrder(mapper, handle);
            order.verify(mapper).mapPacket(first, firstTime, 0, 60, 100);
            order.verify(mapper).mapPacket(skipped, skippedTime, 1, 14, 14);
            order.verify(mapper).mapPacket(last, lastTime, 2, 80, 120);
            order.verify(handle).close();
            verifyNoMoreInteractions(mapper);
            verify(handle, times(4)).getNextPacketEx();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sample.pcapng", "sample.PCAPNG", "sample.pcap", "sample.PCAP", "sample.bin", "sample"})
    void characterizesCurrentExtensionDetection(String fileName) throws Exception {
        String path = Path.of("captures", fileName).toString();
        PcapHandle handle = mock(PcapHandle.class);
        when(handle.getNextPacketEx()).thenThrow(new PcapNativeException("read failed"));
        try (MockedStatic<Pcaps> pcaps = mockStatic(Pcaps.class)) {
            pcaps.when(() -> Pcaps.openOffline(path)).thenReturn(handle);
            Capture capture = new PcapReader().readFromFile(path);

            assertEquals(fileName, capture.getSourceFileName());
            // Known issue: .pcap currently returns PCAPNG too. This is characterization,
            // not an assertion that format detection is correct.
            FileFormat expected = fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".pcapng")
                    || fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".pcap")
                    ? FileFormat.PCAPNG : null;
            assertEquals(expected, capture.getFileFormat());
            assertTrue(capture.getPackets().isEmpty());
            verify(handle).close();
        }
    }

    @Test
    void currentlyPropagatesEofAndClosesHandleInsteadOfReturningCapture() throws Exception {
        EOFException eof = new EOFException("end of capture");
        assertReadFailureClosesHandle(eof);
    }

    @Test
    void propagatesTimeoutAndClosesHandle() throws Exception {
        assertReadFailureClosesHandle(new TimeoutException("timed out"));
    }

    @Test
    void propagatesNotOpenAndClosesHandle() throws Exception {
        assertReadFailureClosesHandle(new NotOpenException("closed"));
    }

    @Test
    void currentlyFailsOnFirstPacketWithoutMapperInitializationAndClosesHandle() throws Exception {
        String path = "sample.pcapng";
        Packet packet = mock(Packet.class);
        PcapHandle handle = mock(PcapHandle.class);
        when(handle.getNextPacketEx()).thenReturn(packet);
        when(handle.getTimestamp()).thenReturn(Timestamp.from(Instant.EPOCH));
        when(handle.getOriginalLength()).thenReturn(60);
        when(packet.length()).thenReturn(60);
        try (MockedStatic<Pcaps> pcaps = mockStatic(Pcaps.class)) {
            pcaps.when(() -> Pcaps.openOffline(path)).thenReturn(handle);

            assertThrows(NullPointerException.class, () -> new PcapReader().readFromFile(path));
            verify(handle).close();
        }
    }

    @Test
    void closesHandleWhenMappingFails() throws Exception {
        String path = "sample.pcapng";
        Packet packet = mock(Packet.class);
        PcapHandle handle = mock(PcapHandle.class);
        when(handle.getNextPacketEx()).thenReturn(packet);
        when(handle.getTimestamp()).thenReturn(Timestamp.from(Instant.EPOCH));
        when(handle.getOriginalLength()).thenReturn(60);
        when(packet.length()).thenReturn(60);
        PcapMapper mapper = mock(PcapMapper.class);
        IllegalArgumentException failure = new IllegalArgumentException("invalid packet");
        when(mapper.mapPacket(packet, Instant.EPOCH, 0, 60, 60)).thenThrow(failure);
        PcapReader reader = readerWith(mapper);
        try (MockedStatic<Pcaps> pcaps = mockStatic(Pcaps.class)) {
            pcaps.when(() -> Pcaps.openOffline(path)).thenReturn(handle);

            assertSame(failure, assertThrows(IllegalArgumentException.class, () -> reader.readFromFile(path)));
            verify(handle).close();
        }
    }

    private static void assertReadFailureClosesHandle(Exception failure) throws Exception {
        String path = "sample.pcapng";
        PcapHandle handle = mock(PcapHandle.class);
        when(handle.getNextPacketEx()).thenThrow(failure);
        try (MockedStatic<Pcaps> pcaps = mockStatic(Pcaps.class)) {
            pcaps.when(() -> Pcaps.openOffline(path)).thenReturn(handle);
            assertSame(failure, assertThrows(failure.getClass(), () -> new PcapReader().readFromFile(path)));
            verify(handle).close();
        }
    }

    private static PcapReader readerWith(PcapMapper mapper) throws Exception {
        // No constructor/setter injection is currently available in production.
        PcapReader reader = new PcapReader();
        Field field = PcapReader.class.getDeclaredField("mapper");
        field.setAccessible(true);
        field.set(reader, mapper);
        return reader;
    }

    private static CapturedPacket packet(int number, Instant timestamp, int capturedLength, int originalLength) {
        return new CapturedPacket(number, timestamp, capturedLength, originalLength, 4,
                "192.0.2.1", "198.51.100.2", IpProtocol.UDP, new UdpDetails(53000, 53, 40),
                64, null, null, null);
    }
}
