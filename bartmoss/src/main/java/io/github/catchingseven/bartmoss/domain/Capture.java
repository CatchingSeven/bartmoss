package io.github.catchingseven.bartmoss.domain;
import java.util.ArrayList;
import java.util.List;


public class Capture {
    private int captureId;
    private String sourceFileName;
    private FileFormat fileFormat;
    private List<Packet> packets;

    public Capture(int captureId, String sourceFileName, FileFormat fileFormat){
        this.captureId = captureId;
        this.sourceFileName = sourceFileName;
        this.fileFormat = fileFormat;
        this.packets = new ArrayList<>();

    }

    public int getCaptureId(){
        return captureId;
    }

    public void setCaptureId(int captureId){
        this.captureId = captureId;
    }

    public String getSourceFileName(){
        return sourceFileName;
    }

    public void setSourceFileName(String sourceFileName){
        this.sourceFileName = sourceFileName;
    }

    public FileFormat getFileFormat(){
        return fileFormat;
    }

    public void setFileFormat(FileFormat fileFormat){
        this.fileFormat = fileFormat;
    }

    public List<Packet> getPackets(){
        return packets;
    }

    public void setPackets(List<Packet> packets){
        this.packets = packets;
    }
    
}
