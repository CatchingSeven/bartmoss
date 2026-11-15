package io.github.catchingseven.bartmoss.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static io.github.catchingseven.bartmoss.support.TestObjects.*;
import static io.github.catchingseven.bartmoss.support.FxTestSupport.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.models.responses.ResponseCreateParams;
import io.github.catchingseven.bartmoss.integration.OpenAIClient;
import io.github.catchingseven.bartmoss.support.ExportSandbox;
import io.github.catchingseven.bartmoss.support.NativePcap;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedConstruction;

/** UI -> real application -> native PCAP -> JSON -> offline SDK -> UI -> real exports. */
@Tag("integration")
@Tag("native-pcap")
@Tag("javafx")
class AnalysisUiIntegrationTest {
    @TempDir Path temp;
    @BeforeAll static void prerequisites() throws Exception { NativePcap.requireLibrary(); startToolkit(); }

    @Test
    void completeAnalysisThroughFxmlButtonsAndExportedFiles() throws Exception {
        Path input = writePcap(temp.resolve("ui-pipeline.pcap"));
        var sdk = mock(com.openai.client.OpenAIClient.class, RETURNS_DEEP_STUBS);
        var reply = response("Offline analysis\nUDP packet observed");
        when(sdk.responses().create(any(ResponseCreateParams.class))).thenReturn(reply);
        OpenAIClient adapter = offlineClient(sdk);

        // All scoped construction/static mocks are created and closed on their owning FX thread.
        class Session implements AutoCloseable {
            final MockedConstruction<OpenAIClient> clients;
            final MockedConstruction<Alert> alerts;
            final ExportSandbox files;
            final FXMLLoader loader;
            final Button analyse;
            final Button export;
            final TextArea text;
            final CompletableFuture<String> rendered = new CompletableFuture<>();

            Session() throws Exception {
                clients = mockConstruction(OpenAIClient.class, (client, context) ->
                        when(client.requestResponse(any())).thenAnswer(call -> adapter.requestResponse(call.getArgument(0))));
                alerts = mockConstruction(Alert.class, (alert, context) ->
                        when(alert.showAndWait()).thenReturn(Optional.of(ButtonType.OK)));
                files = new ExportSandbox(temp, true);
                loader = new FXMLLoader(MainController.class.getResource("main-view.fxml"));
                Parent root = loader.load();
                new Scene(root);
                analyse = (Button) loader.getNamespace().get("analyseButton");
                export = (Button) loader.getNamespace().get("exportButton");
                text = (TextArea) loader.getNamespace().get("modelResponseTextArea");
                text.textProperty().addListener((observable, before, after) -> rendered.complete(after));
            }

            public void close() throws Exception {
                fx(() -> { files.close(); alerts.close(); clients.close(); return null; });
            }
        }

        try (Session session = fx(Session::new)) {
            fx(() -> {
                try (var choosers = mockConstruction(FileChooser.class, (chooser, context) -> {
                    when(chooser.getExtensionFilters()).thenReturn(FXCollections.observableArrayList());
                    when(chooser.showOpenDialog(any())).thenReturn(input.toFile());
                })) {
                    ((Button) session.loader.getNamespace().get("selectFileButton")).fire();
                }
                session.analyse.fire();
                return null;
            });
            assertEquals("Offline analysis\nUDP packet observed", session.rendered.get(15, TimeUnit.SECONDS));
            fx(() -> {
                assertFalse(session.export.isDisabled());
                session.export.fire();
                assertTrue(session.export.isDisabled());
                assertEquals(session.text.getText(), Files.readString(session.files.response));
                var json = new ObjectMapper().readTree(Files.readString(session.files.json));
                assertEquals("ui-pipeline.pcap", json.path("sourceFileName").asText());
                assertEquals(53, json.path("packets").get(0).path("protocolDetails").path("destionationPort").asInt());
                return null;
            });
            verify(sdk.responses(), times(1)).create(any(ResponseCreateParams.class));
        }
    }
}
