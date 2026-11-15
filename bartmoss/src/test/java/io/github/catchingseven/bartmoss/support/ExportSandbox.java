package io.github.catchingseven.bartmoss.support;

import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import org.mockito.MockedStatic;

/** Redirects only the two hardcoded export paths; Files.writeString remains real. */
public final class ExportSandbox implements AutoCloseable {
    public final Path json;
    public final Path response;
    private final MockedStatic<LocalDateTime> clock;
    private final MockedStatic<Path> paths;

    public ExportSandbox(Path root, boolean createDirectories) throws Exception {
        json = root.resolve("JsonExports/output2026-11-12_12-30-45.json");
        response = root.resolve("ResponseExports/response2026-11-12_12-30-45.txt");
        if (createDirectories) {
            Files.createDirectories(json.getParent());
            Files.createDirectories(response.getParent());
        }
        LocalDateTime time = LocalDateTime.of(2026, 11, 12, 12, 30, 45);
        clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS);
        clock.when(LocalDateTime::now).thenReturn(time);
        paths = mockStatic(Path.class, call -> {
            if (call.getArgument(0) instanceof String value && value.startsWith("exports/")) {
                return switch (value) {
                    case "exports/JsonExports/output2026-11-12_12-30-45.json" -> json;
                    case "exports/ResponseExports/response2026-11-12_12-30-45.txt" -> response;
                    default -> throw new AssertionError("Unexpected export path: " + value);
                };
            }
            return call.callRealMethod();
        });
    }

    @Override
    public void close() {
        paths.close();
        clock.close();
    }
}
