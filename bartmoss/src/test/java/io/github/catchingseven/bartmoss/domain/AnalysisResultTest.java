package io.github.catchingseven.bartmoss.domain;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AnalysisResultTest {

    @Test
    void constructorAndSettersExposeAnalysisData() {
        Instant initialTime = Instant.parse("2026-10-20T01:02:03Z");
        AnalysisResult result = new AnalysisResult(7, initialTime, "gpt-test", "Initial analysis");

        assertAll("constructor values",
                () -> assertEquals(7, result.getAnalysisId()),
                () -> assertEquals(initialTime, result.getAnalysedAt()),
                () -> assertEquals("gpt-test", result.getModel()),
                () -> assertEquals("Initial analysis", result.getResponseText()));

        Instant updatedTime = Instant.parse("2026-10-20T02:03:04Z");
        result.setAnalysisId(8);
        result.setAnalysedAt(updatedTime);
        result.setModel("gpt-updated");
        result.setResponseText("Updated analysis");

        assertAll("updated values",
                () -> assertEquals(8, result.getAnalysisId()),
                () -> assertEquals(updatedTime, result.getAnalysedAt()),
                () -> assertEquals("gpt-updated", result.getModel()),
                () -> assertEquals("Updated analysis", result.getResponseText()));
    }
}
