package io.github.catchingseven.bartmoss.processing;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.github.catchingseven.bartmoss.domain.Capture;


public class PacketJsonConverter {
    
    /* Well this class implementation was a nice change of pace from the last two */
    public String convertToJson(Capture capture) throws JsonProcessingException{
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        
        String json = mapper.writeValueAsString(capture); 
        return json;
    }
    
}
