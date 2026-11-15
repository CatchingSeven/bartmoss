package io.github.catchingseven.bartmoss.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static io.github.catchingseven.bartmoss.support.TestObjects.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.github.catchingseven.bartmoss.integration.OpenAIClient;
import io.github.catchingseven.bartmoss.persistence.FileExporter;
import io.github.catchingseven.bartmoss.processing.*;
import org.junit.jupiter.api.Test;
import org.pcap4j.core.NotOpenException;

class ApplicationTest {
    /** Constructor mocks isolate dependencies while exercising a real Application. */
    private static final class Dependencies implements AutoCloseable {
        final org.mockito.MockedConstruction<OpenAIClient> clients = mockConstruction(OpenAIClient.class);
        final org.mockito.MockedConstruction<PcapReader> readers = mockConstruction(PcapReader.class);
        final org.mockito.MockedConstruction<PacketJsonConverter> converters = mockConstruction(PacketJsonConverter.class);
        final org.mockito.MockedConstruction<FileExporter> exporters = mockConstruction(FileExporter.class);
        final Application app = new Application();
        final OpenAIClient client = clients.constructed().getFirst();
        final PcapReader reader = readers.constructed().getFirst();
        final PacketJsonConverter converter = converters.constructed().getFirst();
        final FileExporter exporter = exporters.constructed().getFirst();

        void upload(String path, String json) throws Exception {
            var capture = capture();
            when(reader.getCapture()).thenReturn(capture);
            when(converter.convertToJson(capture)).thenReturn(json);
            app.handleUpload(path);
        }

        public void close() {
            exporters.close(); converters.close(); readers.close(); clients.close();
        }
    }

    @Test
    void coordinatesUploadSubmissionAndExportsInOrder() throws Exception {
        try (var d = new Dependencies()) {
            var reply = new ArrayList<>(List.of("first", "second"));
            when(d.client.requestResponse("{\"id\":1}")).thenReturn(reply);
            d.upload("sample.pcap", "{\"id\":1}");
            assertSame(reply, d.app.requestResponse());
            d.app.handleExport("first\nsecond");
            var order = inOrder(d.reader, d.converter, d.client, d.exporter);
            order.verify(d.reader).readFromFile("sample.pcap");
            order.verify(d.reader).getCapture();
            order.verify(d.converter).convertToJson(d.reader.getCapture());
            order.verify(d.client).requestResponse("{\"id\":1}");
            order.verify(d.exporter).exportJson("{\"id\":1}");
            order.verify(d.exporter).exportModelResponse("first\nsecond");
        }
    }

    @Test
    void laterUploadReplacesSubmittedAndExportedJson() throws Exception {
        try (var d = new Dependencies()) {
            d.upload("first.pcap", "first-json");
            d.upload("second.pcapng", "second-json");
            d.app.requestResponse();
            d.app.handleExport("second response");
            verify(d.client).requestResponse("second-json");
            verify(d.exporter).exportJson("second-json");
            verify(d.client, never()).requestResponse("first-json");
        }
    }

    @Test
    void readFailuresStopConversionAndDoNotAutomaticallySubmitOrExport() throws Exception {
        for (Exception failure : List.of(new TimeoutException("timeout"), new NotOpenException("closed"))) {
            try (var d = new Dependencies()) {
                when(d.reader.readFromFile("bad.pcap")).thenThrow(failure);
                assertSame(failure, assertThrows(failure.getClass(), () -> d.app.handleUpload("bad.pcap")));
                verifyNoInteractions(d.converter, d.client, d.exporter);
            }
        }
    }

    @Test
    void serializationFailureStopsUploadAndRetainsOriginalCause() throws Exception {
        try (var d = new Dependencies()) {
            var failure = new JsonProcessingException("serialization failed") { };
            when(d.converter.convertToJson(any())).thenThrow(failure);
            assertSame(failure, assertThrows(JsonProcessingException.class,
                    () -> d.app.handleUpload("sample.pcap")));
            verifyNoInteractions(d.client, d.exporter);
        }
    }

    @Test
    void jsonExportFailurePreventsResponseExport() throws Exception {
        try (var d = new Dependencies()) {
            d.upload("sample.pcap", "{}");
            var failure = new IOException("disk full");
            doThrow(failure).when(d.exporter).exportJson("{}");
            assertSame(failure, assertThrows(IOException.class, () -> d.app.handleExport("response")));
            verify(d.exporter, never()).exportModelResponse(any());
        }
    }

    @Test
    void responseExportFailurePropagatesAfterJsonExport() throws Exception {
        try (var d = new Dependencies()) {
            d.upload("sample.pcap", "{}");
            var failure = new IOException("permission denied");
            doThrow(failure).when(d.exporter).exportModelResponse("response");
            assertSame(failure, assertThrows(IOException.class, () -> d.app.handleExport("response")));
            verify(d.exporter).exportJson("{}");
        }
    }

    @Test
    void returnsProviderSafeErrorWithoutRewritingIt() throws Exception {
        try (var d = new Dependencies()) {
            d.upload("sample.pcap", "{}");
            when(d.client.requestResponse("{}")).thenReturn(new ArrayList<>(List.of("Unable to connect to OpenAI.")));
            assertEquals(List.of("Unable to connect to OpenAI."), d.app.requestResponse());
            verifyNoInteractions(d.exporter);
        }
    }
}
