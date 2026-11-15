package io.github.catchingseven.bartmoss.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static io.github.catchingseven.bartmoss.support.TestObjects.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.errors.RateLimitException;
import com.openai.models.responses.ResponseCreateParams;
import io.github.catchingseven.bartmoss.application.Application;
import io.github.catchingseven.bartmoss.support.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/** Real application, reader, mapper, converter, adapter and filesystem; SDK transport is mocked. */
@Tag("integration")
@Tag("native-pcap")
class AnalysisPipelineIntegrationTest {
    @TempDir Path temp;
    @BeforeAll static void nativeLibrary() { NativePcap.requireLibrary(); }

    @Test
    void captureToJsonToSdkResponseAndBothExports() throws Exception {
        Path input = writePcap(temp.resolve("pipeline.pcap"));
        var sdk = mock(com.openai.client.OpenAIClient.class, RETURNS_DEEP_STUBS);
        var reply = response("First line\nSecond line");
        when(sdk.responses().create(any(ResponseCreateParams.class))).thenReturn(reply);
        OpenAIClient adapter = offlineClient(sdk);
        try (var clients = mockConstruction(OpenAIClient.class, (client, context) ->
                when(client.requestResponse(any())).thenAnswer(call -> adapter.requestResponse(call.getArgument(0))));
                var files = new ExportSandbox(temp, true)) {
            Application app = new Application();
            app.handleUpload(input.toString());
            var lines = app.requestResponse();
            assertEquals(List.of("First line", "Second line"), lines);
            app.handleExport(String.join("\n", lines));
            String exported = Files.readString(files.json);
            assertEquals(1, new ObjectMapper().readTree(exported).path("packets").size());
            assertEquals("First line\nSecond line", Files.readString(files.response));
            var params = ArgumentCaptor.forClass(ResponseCreateParams.class);
            verify(sdk.responses()).create(params.capture());
            assertEquals(ResponseCreateParams.Input.ofText(
                    "Respond in plain text only. Do not use Markdown. Analyse the network traffic, provide a detailed analysis in your response: " + exported),
                    params.getValue().input().orElseThrow());
            verify(clients.constructed().getFirst()).requestResponse(exported);
        }
    }

    @Test
    void rateLimitTravelsThroughAdapterApplicationAndResponseExport() throws Exception {
        Path input = writePcapng(temp.resolve("rate-limit.pcapng"));
        var sdk = mock(com.openai.client.OpenAIClient.class, RETURNS_DEEP_STUBS);
        var failure = mock(RateLimitException.class);
        when(sdk.responses().create(any(ResponseCreateParams.class))).thenThrow(failure);
        OpenAIClient adapter = offlineClient(sdk);
        try (var clients = mockConstruction(OpenAIClient.class, (client, context) ->
                when(client.requestResponse(any())).thenAnswer(call -> adapter.requestResponse(call.getArgument(0))));
                var files = new ExportSandbox(temp, true)) {
            Application app = new Application();
            app.handleUpload(input.toString());
            var lines = app.requestResponse();
            assertEquals(List.of("OpenAI rate limit or quota exceeded."), lines);
            app.handleExport(String.join("\n", lines));
            assertEquals(lines.getFirst(), Files.readString(files.response));
            assertEquals(1, new ObjectMapper().readTree(Files.readString(files.json)).path("packets").size());
        }
    }

    @Test
    void filesystemFailurePropagatesAndDoesNotWriteResponse() throws Exception {
        Path input = writePcap(temp.resolve("storage.pcap"));
        try (var clients = mockConstruction(OpenAIClient.class); var files = new ExportSandbox(temp, false)) {
            Application app = new Application();
            app.handleUpload(input.toString());
            assertThrows(IOException.class, () -> app.handleExport("offline analysis"));
            assertFalse(Files.exists(files.response));
            verifyNoInteractions(clients.constructed().getFirst());
        }
    }

    @Test
    void responseWriteFailureLeavesJsonExportAndPropagates() throws Exception {
        Path input = writePcap(temp.resolve("partial-export.pcap"));
        try (var clients = mockConstruction(OpenAIClient.class); var files = new ExportSandbox(temp, true)) {
            // A directory at the target filename causes a deterministic write failure.
            Files.createDirectory(files.response);
            Application app = new Application();
            app.handleUpload(input.toString());
            assertThrows(IOException.class, () -> app.handleExport("analysis"));
            assertEquals(1, new ObjectMapper().readTree(Files.readString(files.json)).path("packets").size());
            assertTrue(Files.isDirectory(files.response));
        }
    }

    @Test
    @Tag("known-defect")
    void malformedCaptureIsRejectedBeforeItCanReachModel() throws Exception {
        Path input = Files.writeString(temp.resolve("invalid.pcap"), "this is not a capture");
        try (var clients = mockConstruction(OpenAIClient.class)) {
            Application app = new Application();
            // A rejected upload must signal failure so the controller's Task cannot submit it.
            assertThrows(Exception.class, () -> app.handleUpload(input.toString()),
                    "Malformed captures must not silently become an empty JSON submission");
            verifyNoInteractions(clients.constructed().getFirst());
        }
    }
}
