package io.github.catchingseven.bartmoss.persistence;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import io.github.catchingseven.bartmoss.support.ExportSandbox;

class FileExporterTest {
    @TempDir Path temp;
    private final FileExporter exporter = new FileExporter();

    @Test
    void writesJsonAndResponseToDistinctTimestampedUtf8Files() throws Exception {
        try (var files = new ExportSandbox(temp, true)) {
            String json = "{\"source\":\"\u2603\"}";
            String response = "Analysis \u2014 safe\nSecond line";
            exporter.exportJson(json);
            exporter.exportModelResponse(response);
            assertEquals(json, Files.readString(files.json));
            assertEquals(response, Files.readString(files.response));
        }
    }

    @Test
    void supportsEmptyContents() throws Exception {
        try (var files = new ExportSandbox(temp, true)) {
            exporter.exportJson("");
            exporter.exportModelResponse("");
            assertEquals(0, Files.size(files.json));
            assertEquals(0, Files.size(files.response));
        }
    }

    @Test
    void propagatesMissingDirectoryFailuresForBothFormats() throws Exception {
        try (var files = new ExportSandbox(temp, false)) {
            assertThrows(NoSuchFileException.class, () -> exporter.exportJson("{}"));
            assertThrows(NoSuchFileException.class, () -> exporter.exportModelResponse("analysis"));
            assertFalse(Files.exists(files.json));
            assertFalse(Files.exists(files.response));
        }
    }

    @Test
    void characterizesSameSecondExportsOverwritingEarlierContents() throws Exception {
        // Documents the current collision policy; this is not a uniqueness guarantee.
        try (var files = new ExportSandbox(temp, true)) {
            exporter.exportJson("first");
            exporter.exportJson("second");
            exporter.exportModelResponse("first response");
            exporter.exportModelResponse("second response");
            assertEquals("second", Files.readString(files.json));
            assertEquals("second response", Files.readString(files.response));
        }
    }
}
