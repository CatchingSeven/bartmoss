package io.github.catchingseven.bartmoss.presentation;

import java.io.File;
import java.util.ArrayList;

import ch.qos.logback.core.model.Model;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.scene.control.Alert;
import javafx.concurrent.Task;
import io.github.catchingseven.bartmoss.application.*;

public class MainController {
    @FXML
    private Button selectFileButton;
    @FXML
    private Button analyseButton;
    @FXML
    private Button exportButton;
    @FXML
    private TextField selectedPathTextField;
    @FXML
    private TextArea modelResponseTextArea;
    private String selectedFilePath;
    private Application application = new Application();

    @FXML
    private void handleSelectFile() {
        FileChooser fileChooser = new FileChooser();

        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Packet Captures", "*.pcap", "*.pcapng"));
        File file = fileChooser.showOpenDialog(selectFileButton.getScene().getWindow());

        if (file != null) {
            selectedFilePath = file.getAbsolutePath();
            selectedPathTextField.setText(selectedFilePath);
            analyseButton.setDisable(false);
        }
    }

    @FXML
    private void handleAnalyse() {

        Task<ArrayList<String>> task = new Task<ArrayList<String>>() {

            @Override
            protected ArrayList<String> call() throws Exception {
                application.handleUpload(selectedFilePath);
                analyseButton.setDisable(true);
                return application.requestResponse();

            }
        };

        task.setOnSucceeded(event -> {
            ArrayList<String> response = task.getValue();
            modelResponseTextArea.setText(String.join("\n", response));
            analyseButton.setDisable(false);
            exportButton.setDisable(false);

        });

        task.setOnFailed(event -> {

            Throwable e = task.getException();

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Analysis Failed!");
            alert.setHeaderText("Unable to Analyse capture");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
            analyseButton.setDisable(false);

        });

        Thread thread = new Thread(task);
        thread.start();

    }

    @FXML
    private void handleExport() {

        try {

            application.handleExport(modelResponseTextArea.getText());
            exportButton.setDisable(true);
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Export Failed");
            alert.setHeaderText("Successfully Exported to: bartmoss\\exports\\");
            alert.showAndWait();

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Export Failed");
            alert.setHeaderText("Unable to export json and response");
            alert.setContentText(e.getMessage());
            alert.showAndWait();

        }

    }

}
