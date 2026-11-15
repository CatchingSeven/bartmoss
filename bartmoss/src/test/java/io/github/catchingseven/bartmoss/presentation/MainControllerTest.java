package io.github.catchingseven.bartmoss.presentation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static io.github.catchingseven.bartmoss.support.FxTestSupport.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import io.github.catchingseven.bartmoss.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

/** Real FXML and controls; only application services and modal OS dialogs are doubled. */
@Tag("javafx")
class MainControllerTest {
    @BeforeAll static void toolkit() throws Exception { startToolkit(); }

    private static final class View implements AutoCloseable {
        final MockedConstruction<Application> applications;
        final MockedConstruction<Alert> alerts;
        final List<Alert.AlertType> alertTypes = new ArrayList<>();
        final CompletableFuture<Alert> shownAlert = new CompletableFuture<>();
        final Application app;
        final Parent root;
        final MainController controller;
        final Button select;
        final Button analyse;
        final Button export;
        final TextField path;
        final TextArea response;

        View() throws Exception {
            applications = mockConstruction(Application.class);
            alerts = mockConstruction(Alert.class, (alert, context) -> {
                    alertTypes.add((Alert.AlertType) context.arguments().getFirst());
                    when(alert.showAndWait()).thenAnswer(call -> {
                        shownAlert.complete(alert);
                        return Optional.of(ButtonType.OK);
                    });
            });
            var resource = MainController.class.getResource("main-view.fxml");
            assertNotNull(resource, "The production FXML must be packaged");
            FXMLLoader loader = new FXMLLoader(resource);
            root = loader.load();
            new Scene(root);
            controller = loader.getController();
            app = applications.constructed().getFirst();
            select = (Button) loader.getNamespace().get("selectFileButton");
            analyse = (Button) loader.getNamespace().get("analyseButton");
            export = (Button) loader.getNamespace().get("exportButton");
            path = (TextField) loader.getNamespace().get("selectedPathTextField");
            response = (TextArea) loader.getNamespace().get("modelResponseTextArea");
        }

        void choose(File file) {
            try (var choosers = mockConstruction(FileChooser.class, (chooser, context) -> {
                when(chooser.getExtensionFilters()).thenReturn(FXCollections.observableArrayList());
                when(chooser.showOpenDialog(any())).thenReturn(file);
            })) {
                select.fire();
                var chooser = choosers.constructed().getFirst();
                assertEquals(List.of("*.pcap", "*.pcapng"), chooser.getExtensionFilters().getFirst().getExtensions());
                verify(chooser).showOpenDialog(null);
            }
        }

        CompletableFuture<String> nextResponse() {
            var changed = new CompletableFuture<String>();
            response.textProperty().addListener((observable, before, after) -> changed.complete(after));
            return changed;
        }

        public void close() throws Exception {
            // Mockito construction scopes belong to the FX thread that opened them.
            fx(() -> { alerts.close(); applications.close(); return null; });
        }
    }

    @Test
    void loadsFxmlInjectsControllerAndStartsWithActionsDisabled() throws Exception {
        try (View v = fx(View::new)) {
            fx(() -> {
                assertNotNull(v.controller);
                assertNotNull(v.select.getOnAction());
                assertNotNull(v.analyse.getOnAction());
                assertNotNull(v.export.getOnAction());
                assertFalse(v.select.isDisabled());
                assertTrue(v.analyse.isDisabled());
                assertTrue(v.export.isDisabled());
                assertEquals("selected file path", v.path.getText());
                assertEquals("model response will appear here", v.response.getText());
                assertEquals(1, v.root.getStylesheets().size());
                assertNotNull(MainController.class.getResource("styles.css"));
                verifyNoInteractions(v.app);
                return null;
            });
        }
    }

    @Test
    void selectingCaptureUpdatesPathAndEnablesAnalysis() throws Exception {
        try (View v = fx(View::new)) {
            fx(() -> {
                File file = new File("selected.pcapng");
                v.choose(file);
                assertEquals(file.getAbsolutePath(), v.path.getText());
                assertFalse(v.analyse.isDisabled());
                assertTrue(v.export.isDisabled());
                verifyNoInteractions(v.app);
                return null;
            });
        }
    }

    @Test
    void cancellingChooserPreservesSelectionAndActionState() throws Exception {
        try (View v = fx(View::new)) {
            fx(() -> {
                v.choose(null);
                assertTrue(v.analyse.isDisabled());
                assertEquals("selected file path", v.path.getText());
                File selected = new File("retained.pcap");
                v.choose(selected);
                v.choose(null);
                assertEquals(selected.getAbsolutePath(), v.path.getText());
                assertFalse(v.analyse.isDisabled());
                return null;
            });
        }
    }

    @Test
    void analysisRunsOffFxThreadDisplaysLinesAndEnablesExport() throws Exception {
        try (View v = fx(View::new)) {
            AtomicBoolean background = new AtomicBoolean();
            when(v.app.requestResponse()).thenAnswer(call -> {
                background.set(!Platform.isFxApplicationThread());
                return new ArrayList<>(List.of("First line", "Second line"));
            });
            CompletableFuture<String> rendered = fx(() -> {
                v.choose(new File("analysis.pcap"));
                var result = v.nextResponse();
                v.analyse.fire();
                return result;
            });
            assertEquals("First line\nSecond line", rendered.get(15, TimeUnit.SECONDS));
            fx(() -> {
                assertFalse(v.analyse.isDisabled());
                assertFalse(v.export.isDisabled());
                assertTrue(v.alerts.constructed().isEmpty());
                return null;
            });
            assertTrue(background.get());
            var order = inOrder(v.app);
            order.verify(v.app).handleUpload(new File("analysis.pcap").getAbsolutePath());
            order.verify(v.app).requestResponse();
        }
    }

    @Test
    void uploadFailureShowsMessageAndAllowsRetryWithoutRequestingModel() throws Exception {
        try (View v = fx(View::new)) {
            doThrow(new IllegalArgumentException("invalid capture")).when(v.app).handleUpload(any());
            fx(() -> { v.choose(new File("invalid.pcap")); v.analyse.fire(); return null; });
            Alert alert = v.shownAlert.get(15, TimeUnit.SECONDS);
            fx(() -> {
                verify(alert).setTitle("Analysis Failed!");
                assertEquals(List.of(Alert.AlertType.ERROR), v.alertTypes);
                verify(alert).setContentText("invalid capture");
                assertFalse(v.analyse.isDisabled());
                assertTrue(v.export.isDisabled());
                assertEquals("model response will appear here", v.response.getText());
                return null;
            });
            verify(v.app, never()).requestResponse();
            doNothing().when(v.app).handleUpload(any());
            when(v.app.requestResponse()).thenReturn(new ArrayList<>(List.of("retry succeeded")));
            var rendered = fx(() -> { var result = v.nextResponse(); v.analyse.fire(); return result; });
            assertEquals("retry succeeded", rendered.get(15, TimeUnit.SECONDS));
            fx(() -> { assertFalse(v.export.isDisabled()); return null; });
        }
    }

    @Test
    void unexpectedModelFailureShowsUiErrorAndRestoresAnalysisButton() throws Exception {
        try (View v = fx(View::new)) {
            when(v.app.requestResponse()).thenThrow(new IllegalStateException("model failed"));
            fx(() -> { v.choose(new File("model-error.pcap")); v.analyse.fire(); return null; });
            Alert alert = v.shownAlert.get(15, TimeUnit.SECONDS);
            fx(() -> {
                verify(alert).setContentText("model failed");
                assertEquals(List.of(Alert.AlertType.ERROR), v.alertTypes);
                assertFalse(v.analyse.isDisabled());
                assertTrue(v.export.isDisabled());
                return null;
            });
        }
    }

    @Test
    void providerSafeErrorTextIsDisplayedToUser() throws Exception {
        try (View v = fx(View::new)) {
            when(v.app.requestResponse()).thenReturn(new ArrayList<>(List.of("Unable to connect to OpenAI.")));
            var rendered = fx(() -> {
                v.choose(new File("offline.pcap")); var result = v.nextResponse(); v.analyse.fire(); return result;
            });
            assertEquals("Unable to connect to OpenAI.", rendered.get(15, TimeUnit.SECONDS));
            fx(() -> { assertFalse(v.analyse.isDisabled()); return null; });
        }
    }

    @Test
    void successfulExportPassesDisplayedTextAndDisablesRepeatExport() throws Exception {
        try (View v = fx(View::new)) {
            fx(() -> {
                v.response.setText("analysis\nsecond line"); v.export.setDisable(false); v.export.fire();
                verify(v.app).handleExport("analysis\nsecond line");
                assertTrue(v.export.isDisabled());
                assertEquals(List.of(Alert.AlertType.CONFIRMATION), v.alertTypes);
                verify(v.alerts.constructed().getFirst()).setHeaderText("Successfully Exported to: bartmoss\\exports\\");
                v.export.fire();
                verify(v.app, times(1)).handleExport(any());
                return null;
            });
        }
    }

    @Test
    void exportFailureDisplaysErrorPreservesTextAndAllowsRetry() throws Exception {
        try (View v = fx(View::new)) {
            doThrow(new IOException("disk full")).when(v.app).handleExport(any());
            fx(() -> {
                v.response.setText("keep this analysis"); v.export.setDisable(false); v.export.fire();
                Alert alert = v.alerts.constructed().getFirst();
                verify(alert).setTitle("Export Failed");
                verify(alert).setContentText("disk full");
                assertEquals(List.of(Alert.AlertType.ERROR), v.alertTypes);
                assertEquals("keep this analysis", v.response.getText());
                assertFalse(v.export.isDisabled());
                return null;
            });
        }
    }

    @Test
    @Tag("known-defect")
    void successfulExportMustNotUseFailureTitle() throws Exception {
        try (View v = fx(View::new)) {
            fx(() -> {
                v.export.setDisable(false); v.export.fire();
                var title = org.mockito.ArgumentCaptor.forClass(String.class);
                verify(v.alerts.constructed().getFirst()).setTitle(title.capture());
                assertFalse(title.getValue().toLowerCase(java.util.Locale.ROOT).contains("fail"),
                        "Successful export currently displays a failure title");
                return null;
            });
        }
    }

    @Test
    @Tag("known-defect")
    void analysisControlStateChangesMustStayOnFxThread() throws Exception {
        try (View v = fx(View::new)) {
            AtomicBoolean changedOffFxThread = new AtomicBoolean();
            CountDownLatch enteredRequest = new CountDownLatch(1);
            CountDownLatch finishRequest = new CountDownLatch(1);
            when(v.app.requestResponse()).thenAnswer(call -> {
                enteredRequest.countDown();
                if (!finishRequest.await(10, TimeUnit.SECONDS)) throw new AssertionError("request not released");
                return new ArrayList<>(List.of("done"));
            });
            var rendered = fx(() -> {
                v.choose(new File("threading.pcap"));
                v.analyse.disabledProperty().addListener((observable, before, after) -> {
                    if (!Platform.isFxApplicationThread()) changedOffFxThread.set(true);
                });
                var result = v.nextResponse(); v.analyse.fire(); return result;
            });
            try {
                assertTrue(enteredRequest.await(10, TimeUnit.SECONDS));
            } finally {
                finishRequest.countDown();
            }
            assertEquals("done", rendered.get(15, TimeUnit.SECONDS));
            assertFalse(changedOffFxThread.get(), "The worker Task changes analyseButton outside the FX thread");
        }
    }
}
