package io.github.catchingseven.bartmoss;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

import org.pcap4j.core.NotOpenException;

import com.fasterxml.jackson.core.JsonProcessingException;
//import com.openai.models.beta.agents.AgentToolError.Stage;

import io.github.catchingseven.bartmoss.application.*;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.TextArea;
import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/io/github/catchingseven/bartmoss/presentation/main-view.fxml"));

        Scene scene = new Scene(loader.load());

        stage.setTitle("Bartmoss");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);

    }

}
