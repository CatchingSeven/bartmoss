package io.github.catchingseven.bartmoss.domain;

import java.util.Set;

public record TcpDetails(
    int sourcePort,
    int destionationPort,
    long sequenceNumber,
    long acknowledgementNumber,
    Set<TcpFlag> flags,
    int windowSize

) implements ProtocolDetails {


}