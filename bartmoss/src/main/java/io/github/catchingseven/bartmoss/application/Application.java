package io.github.catchingseven.bartmoss.application;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.concurrent.StructuredTaskScope.TimeoutException;

import org.pcap4j.core.NotOpenException;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.github.catchingseven.bartmoss.integration.OpenAIClient;

import io.github.catchingseven.bartmoss.processing.PacketJsonConverter;
import io.github.catchingseven.bartmoss.processing.PcapReader;

public class Application {
    private OpenAIClient openAIClient;
    private PcapReader reader;
    private PacketJsonConverter converter;
    private String json;

    public Application() {
        openAIClient = new OpenAIClient();
        reader = new PcapReader();
        converter = new PacketJsonConverter();
    }

    public void handleUpload(String pathToFile)
            throws EOFException, JsonProcessingException, NotOpenException, java.util.concurrent.TimeoutException {
        reader.readFromFile(pathToFile);
        json = converter.convertToJson(reader.getCapture());

    }

    public ArrayList<String> requestResponse() { // wrapper for OpenAIClient requestResponse method
        return openAIClient.requestResponse(json);
    }

}
