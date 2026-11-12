package io.github.catchingseven.bartmoss.persistence;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class FileExporter {

    public void exportJson(String json) throws IOException {
        String exportPath = "exports/JsonExports/output" + dateTimeString() + ".json";
        Path path = Path.of(exportPath);
        Files.writeString(path, json);

    }

    public void exportModelResponse(String response) throws IOException {
        String exportPath = "exports/ResponseExports/response" + dateTimeString() + ".txt";
        Path path = Path.of(exportPath);
        Files.writeString(path, response);

    }

    private String dateTimeString() {
        LocalDateTime now = LocalDateTime.now();
        return now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

    }

}
