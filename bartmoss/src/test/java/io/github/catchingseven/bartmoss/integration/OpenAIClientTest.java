package io.github.catchingseven.bartmoss.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static io.github.catchingseven.bartmoss.support.TestObjects.*;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.stream.Stream;
import com.openai.errors.*;
import com.openai.models.responses.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

class OpenAIClientTest {
    @Test
    void submitsJsonWithConfiguredModelAndReturnsLinesInOrder() throws Exception {
        var sdk = mock(com.openai.client.OpenAIClient.class, RETURNS_DEEP_STUBS);
        Response reply = response("First\r\nSecond\n\nThird", "Fourth");
        when(sdk.responses().create(any(ResponseCreateParams.class))).thenReturn(reply);
        assertEquals(List.of("First", "Second", "", "Third", "Fourth"),
                offlineClient(sdk).requestResponse("{\"packets\":[]}"));
        var params = ArgumentCaptor.forClass(ResponseCreateParams.class);
        verify(sdk.responses()).create(params.capture());
        assertEquals("gpt-6-luna", params.getValue().model().orElseThrow().asString());
        assertEquals(ResponseCreateParams.Input.ofText(
                "Respond in plain text only. Do not use Markdown. Analyse the network traffic, provide a detailed analysis in your response: {\"packets\":[]}"),
                params.getValue().input().orElseThrow());
    }

    @Test
    void ignoresNonMessageOutputsAndRefusals() throws Exception {
        var sdk = mock(com.openai.client.OpenAIClient.class, RETURNS_DEEP_STUBS);
        Response result = mock(Response.class);
        ResponseOutputItem nonMessage = mock(ResponseOutputItem.class);
        var message = ResponseOutputMessage.builder().id("offline-refusal")
                .status(ResponseOutputMessage.Status.COMPLETED).addRefusalContent("Refused").build();
        when(result.output()).thenReturn(List.of(nonMessage, ResponseOutputItem.ofMessage(message)));
        when(sdk.responses().create(any(ResponseCreateParams.class))).thenReturn(result);
        assertTrue(offlineClient(sdk).requestResponse("{}").isEmpty());
    }

    @Test
    void handlesEmptyOutput() throws Exception {
        var sdk = mock(com.openai.client.OpenAIClient.class, RETURNS_DEEP_STUBS);
        Response result = mock(Response.class);
        when(result.output()).thenReturn(List.of());
        when(sdk.responses().create(any(ResponseCreateParams.class))).thenReturn(result);
        assertTrue(offlineClient(sdk).requestResponse("{}").isEmpty());
    }

    static Stream<Arguments> providerErrors() {
        OpenAIServiceException service = mock(OpenAIServiceException.class);
        when(service.statusCode()).thenReturn(503);
        return Stream.of(
                Arguments.of(mock(UnauthorizedException.class), "OpenAI authentication failed."),
                Arguments.of(mock(RateLimitException.class), "OpenAI rate limit or quota exceeded."),
                Arguments.of(new OpenAIIoException("private transport details"), "Unable to connect to OpenAI."),
                Arguments.of(service, "OpenAI API error: HTTP 503"));
    }

    @ParameterizedTest
    @MethodSource("providerErrors")
    void convertsProviderFailuresToSafeMessages(RuntimeException failure, String expected) throws Exception {
        var sdk = mock(com.openai.client.OpenAIClient.class, RETURNS_DEEP_STUBS);
        when(sdk.responses().create(any(ResponseCreateParams.class))).thenThrow(failure);
        assertEquals(List.of(expected), offlineClient(sdk).requestResponse("{}"));
        verify(sdk.responses(), times(1)).create(any(ResponseCreateParams.class));
    }

    @Test
    void doesNotSwallowUnexpectedProgrammingErrors() throws Exception {
        var sdk = mock(com.openai.client.OpenAIClient.class, RETURNS_DEEP_STUBS);
        var failure = new IllegalArgumentException("unexpected failure");
        when(sdk.responses().create(any(ResponseCreateParams.class))).thenThrow(failure);
        assertSame(failure, assertThrows(IllegalArgumentException.class,
                () -> offlineClient(sdk).requestResponse("{}")));
    }

    @Test
    void rejectsMissingOrBlankKeysWithoutReadingOrChangingEnvironment() throws Exception {
        // The private validator is the only credential-free seam for constructor validation.
        var validator = OpenAIClient.class.getDeclaredMethod("validateKey", String.class);
        validator.setAccessible(true);
        for (String key : new String[]{null, "", " ", "\t\n"}) {
            var failure = assertThrows(InvocationTargetException.class, () -> validator.invoke(null, key));
            assertInstanceOf(IllegalStateException.class, failure.getCause());
            assertEquals("OPENAI_API_KEY is missing", failure.getCause().getMessage());
        }
        assertDoesNotThrow(() -> validator.invoke(null, "offline-placeholder"));
    }
}
