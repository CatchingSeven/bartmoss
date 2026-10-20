package io.github.catchingseven.bartmoss.domain;

import java.time.Instant;

public class CapturedPacket {

    private int packetNumber;
    private Instant timestamp;
    private int capturedLength;
    private int originalLength;
    private int ipVersion;
    private String sourceIpAddress;
    private String destinationIpAddress;
    private IpProtocol ipProtocol;
    private ProtocolDetails protocolDetails;
    private int ttlOrHopLimit;
    /*these bottom three aren't high priority right now*/
    private Long fragementIdentification;
    private Integer fragmentOffset;
    private Boolean moreFragments;

    public CapturedPacket (int packetNumber, Instant timestamp, int capturedLength, int originalLength, int ipVersion, 
                    String sourceIpAddress, String destinationIpAddress, IpProtocol ipProtocol, ProtocolDetails protocolDetails, int ttlOrHopLimit, Long fragementIdentification, Integer fragmentOffset, Boolean moreFragments){
    this.packetNumber = packetNumber;
    this.timestamp = timestamp;
    this.capturedLength = capturedLength;
    this.originalLength = originalLength;
    this.ipVersion = ipVersion;
    this.sourceIpAddress = sourceIpAddress;
    this.destinationIpAddress = destinationIpAddress;
    this.ipProtocol = ipProtocol;
    this.protocolDetails = protocolDetails;
    this.ttlOrHopLimit = ttlOrHopLimit;
    this.fragementIdentification = fragementIdentification;
    this.fragmentOffset = fragmentOffset;
    this.moreFragments = moreFragments;


    }

    public void setPacketNumber(int packetNumber){
        this.packetNumber = packetNumber;
    }

    public int getPacketNumber(){
        return packetNumber;
    }

    public Instant getTimestamp(){
        return timestamp;
    }

    public void setTimestamp(Instant timestamp){
        this.timestamp = timestamp;
    }

    public int getCapturedLength(){
        return capturedLength;
    }

    public void setCapturedLength(int capturedLength){
        this.capturedLength = capturedLength;
    }

    public int getOriginalLength(){
        return originalLength;
    }

    public void setOriginalLength(int originalLength){
        this.originalLength = originalLength;
    }

    public int getIpVersion(){
        return ipVersion;
    }

    public void setIpVersion(int ipVersion){
        this.ipVersion = ipVersion;
    }

    public String getSourceIpAddress(){
        return sourceIpAddress;
    }

    public void setSourceIpAddress(String sourceIpAddress){
        this.sourceIpAddress = sourceIpAddress;
    }

    public String getDestinationIpAddress(){
        return destinationIpAddress;
    }

    public void setDestinationIpAddress(String destinationIpAddress){
        this.destinationIpAddress = destinationIpAddress;
    }

    public IpProtocol getIpProtocol(){
        return ipProtocol;
    }

    public void setIpProtocol(IpProtocol ipProtocol){
        this.ipProtocol = ipProtocol;
    }




    public int getTtlOrHopLimit(){
        return ttlOrHopLimit;
    }

    public void setTtlOrHopLimit(int ttlOrHopLimit){
        this.ttlOrHopLimit = ttlOrHopLimit;
    }

    public Long getFragementIdentification(){
        return fragementIdentification;
    }

    public void setFragementIdentification(Long fragementIdentification){
        this.fragementIdentification = fragementIdentification;
    }

    public Integer getFragmentOffset(){
        return fragmentOffset;
    }

    public void setFragmentOffset(Integer fragmentOffset){
        this.fragmentOffset = fragmentOffset;
    }

    public Boolean getMoreFragments(){
        return moreFragments;
    }

    public void setMoreFragments(Boolean moreFragments){
        this.moreFragments = moreFragments;
    }



    
}
