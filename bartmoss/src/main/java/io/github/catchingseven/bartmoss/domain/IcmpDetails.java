package io.github.catchingseven.bartmoss.domain;

public record IcmpDetails(
    int version,
    int type,
    int code,
    Integer identifier, //last two integers because they are both not always present
    Integer sequenceNumber 


) implements ProtocolDetails {

}
