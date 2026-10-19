package io.github.catchingseven.bartmoss.domain;

public class Packet {

    private int packetNumber;
    private int timestamp;
    private int capturedLength;
    private int originalLength;
    private int ipVersion;
    private String sourceIpAddress;
    private String destinationIpAddress;
    private IpProtocol ipProtocol;
    private int ttlOrHopLimit;
    private Long fragementIdentification;
    private Integer fragmentOffset;
    private Boolean moreFragments;

    public Packet (int packetNumber, int timestamp, int capturedLength, int originalLength, int ipVersion, 
                    String sourceIpAddress, String destinationIpAddress, IpProtocol ipProtocol, int ttlOrHopLimit, Long fragementIdentification, Integer fragmentOffset, Boolean moreFragments){
    this.packetNumber = packetNumber;
    this.timestamp = timestamp;
    this.capturedLength = capturedLength;
    this.originalLength = originalLength;
    this.ipVersion = ipVersion;
    this.sourceIpAddress = sourceIpAddress;
    this.destinationIpAddress = destinationIpAddress;
    this.ipProtocol = ipProtocol;
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

    public int getTimestamp(){
        return timestamp;
    }

    public void setTimestamp(int timestamp){
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
