package io.github.catchingseven.bartmoss.domain;

public record UdpDetails(
    int sourcePort,
    int destionationPort,
    int length
) implements ProtocolDetails{

} 
