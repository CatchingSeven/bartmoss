package io.github.catchingseven.bartmoss.integration;

import java.util.ArrayList;
import java.util.stream.Collectors;

import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;

public class OpenAIClient {
    private String apiKey;
    private final com.openai.client.OpenAIClient client;

    public OpenAIClient() {
        apiKey = System.getenv("OPENAI_API_KEY");
        validateKey(apiKey);

        client = OpenAIOkHttpClient.builder().apiKey(apiKey).build();

    }

    public ArrayList<String> requestResponse(String json) {
        ArrayList<String> modelResponse = new ArrayList<>();

        try {
            ResponseCreateParams params = ResponseCreateParams.builder().model("gpt-6-luna")
                    .input("Analyse the network traffic, provide a detailed analysis in your response: " + json)
                    .build();

            Response response = client.responses().create(params);

            modelResponse = response.output().stream().flatMap(item -> item.message().stream())
                    .flatMap(message -> message.content().stream()).flatMap(content -> content.outputText().stream())
                    .flatMap(output -> output.text().lines()).collect(Collectors.toCollection(ArrayList::new));

        } catch (UnauthorizedException e) {
            modelResponse.add("OpenAI authentication failed.");
        } catch (RateLimitException e) {
            modelResponse.add("OpenAI rate limit or quota exceeded.");
        } catch (OpenAIIoException e) {
            modelResponse.add("Unable to connect to OpenAI.");
        } catch (OpenAIServiceException e) {
            modelResponse.add("OpenAI API error: HTTP " + e.statusCode());
        }

        return modelResponse;

    }

    private static void validateKey(String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is missing");

        }

    }

}
