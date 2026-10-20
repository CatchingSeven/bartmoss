package io.github.catchingseven.bartmoss.integration;

import com.openai.client.okhttp.OpenAIOkHttpClient;
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

    public void requestResponse(String json) {
        ResponseCreateParams params = ResponseCreateParams.builder().model("gpt-6-luna")
                .input("Analyse the network traffic, provide a detailed analysis in your response: " + json).build();
        Response response = client.responses().create(params);

        response.output().stream().flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream()).flatMap(content -> content.outputText().stream())
                .forEach(outputText -> System.out.println(outputText.text()));

    }

    private static void validateKey(String apiKey) {

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("OPENAI_API_KEY is missing");

        }

    }

}
