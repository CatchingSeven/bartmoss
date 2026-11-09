package io.github.catchingseven.bartmoss.presentation;

import java.io.File;
import java.util.ArrayList;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.scene.control.Alert;
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
        try {
            application.handleUpload(selectedFilePath);
            ArrayList<String> response = application.requestResponse();
            modelResponseTextArea.setText(String.join("\n", response));
            exportButton.setDisable(false);
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Analysis Failed!");
            alert.setHeaderText("Unable to Analyse capture");
            alert.setContentText(e.getMessage());
            alert.showAndWait();

        }

    }

    @FXML
    private void handleExport() {

    }

}
