package io.github.catchingseven.bartmoss.domain;

import java.time.Instant;

public class AnalysisResult {
    private int analysisId;
    private Instant analysedAt;
    private String model;
    private String responseText;

    public AnalysisResult(int analysisId, Instant analysedAt, String model, String responseText){
        this.analysisId = analysisId;
        this.analysedAt = analysedAt;
        this.model = model;
        this.responseText = responseText;

    }

    public void setAnalysisId(int analysisId){
        this.analysisId = analysisId;
    }

    public int getAnalysisId(){
        return analysisId;
    }

    public void setAnalysedAt(Instant analysedAt){
        this.analysedAt = analysedAt;
    }

    public Instant getAnalysedAt(){
        return analysedAt;
    }

    public void setModel(String model){
        this.model = model;
    }

    public String getModel(){
        return model;
    }

    public void setResponseText(String responseText){
        this.responseText = responseText;
    }

    public String getResponseText(){
        return responseText;
    }



    
}
