package io.github.catchingseven.bartmoss.processing;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import org.pcap4j.packet.IcmpV4CommonPacket;
import org.pcap4j.packet.IcmpV4EchoPacket;
import org.pcap4j.packet.IcmpV4EchoReplyPacket;
import org.pcap4j.packet.IcmpV6CommonPacket;
import org.pcap4j.packet.IcmpV6EchoReplyPacket;
import org.pcap4j.packet.IcmpV6EchoRequestPacket;
import org.pcap4j.packet.IpPacket;
import org.pcap4j.packet.IpV4Packet;
import org.pcap4j.packet.IpV6ExtFragmentPacket;
import org.pcap4j.packet.IpV6Packet;
import org.pcap4j.packet.Packet;
import org.pcap4j.packet.TcpPacket;
import org.pcap4j.packet.UdpPacket;

import io.github.catchingseven.bartmoss.domain.CapturedPacket;
import io.github.catchingseven.bartmoss.domain.FileFormat;
import io.github.catchingseven.bartmoss.domain.IcmpDetails;
import io.github.catchingseven.bartmoss.domain.IpProtocol;
import io.github.catchingseven.bartmoss.domain.ProtocolDetails;
import io.github.catchingseven.bartmoss.domain.TcpDetails;
import io.github.catchingseven.bartmoss.domain.TcpFlag;
import io.github.catchingseven.bartmoss.domain.UdpDetails;
public class PcapMapper {

    public Optional<CapturedPacket> mapPacket(Packet packet, Instant timestamp, int packetNumber, int capturedLength, int originalLength){ //Is optional because if packet isnt valid ipv4 or ipv6 we still want to count it without also making error thrown
        CapturedPacket capturedPacket;
        int ipVersion;
        String sourceIpAddress;
        String destinationIpAddress;
        IpProtocol ipProtocol;
        ProtocolDetails protocolDetails;
        int ttlOrHopLimit;
        Long fragementIdentification = null;
        Integer fragmentOffset = null;
        Boolean moreFragments = null;
        IpPacket ipPacket;



        if (packet.contains(IpV4Packet.class)) {
            ipPacket = packet.get(IpV4Packet.class);
        } 
        else if (packet.contains(IpV6Packet.class)) {
            ipPacket = packet.get(IpV6Packet.class);
            
        } else {
            return Optional.empty();
        }

        IpPacket.IpHeader header = ipPacket.getHeader();
        ipVersion = header.getVersion().value();
        sourceIpAddress = header.getSrcAddr().getHostAddress();
        destinationIpAddress = header.getDstAddr().getHostAddress();
        String protocol = header.getProtocol().name();
        ipProtocol = getProtocolType(protocol);
        protocolDetails = extractProtocolDetails(ipProtocol, ipPacket);


        if (ipVersion == 4){
            IpV4Packet.IpV4Header ipv4Header = (IpV4Packet.IpV4Header) header;
            ttlOrHopLimit = ipv4Header.getTtlAsInt();
            fragementIdentification = (long) ipv4Header.getIdentificationAsInt();
            fragmentOffset = (int) ipv4Header.getFragmentOffset();
            moreFragments = ipv4Header.getMoreFragmentFlag();
        }
        else {
            IpV6Packet.IpV6Header ipv6Header = (IpV6Packet.IpV6Header) header;

            ttlOrHopLimit = ipv6Header.getHopLimitAsInt();
            IpV6ExtFragmentPacket fragment = ipPacket.get(IpV6ExtFragmentPacket.class);
            if (fragment != null){
                IpV6ExtFragmentPacket.IpV6ExtFragmentHeader fragmentHeader = fragment.getHeader();
                fragementIdentification = Integer.toUnsignedLong(fragmentHeader.getIdentification());
                fragmentOffset = (int) fragmentHeader.getFragmentOffset();
                moreFragments = fragmentHeader.getM();
            }
        }






        

        capturedPacket = new CapturedPacket(packetNumber, timestamp, capturedLength, originalLength, ipVersion, sourceIpAddress, destinationIpAddress, ipProtocol, protocolDetails, ttlOrHopLimit, fragementIdentification, fragmentOffset, moreFragments);
        








        return Optional.of(capturedPacket);





    }

    private static ProtocolDetails extractProtocolDetails(IpProtocol ipProtocol, IpPacket ipPacket){
        
        if (ipProtocol == IpProtocol.TCP){
            TcpPacket tcp = ipPacket.get(TcpPacket.class);
            TcpPacket.TcpHeader header = tcp.getHeader();
            Set<TcpFlag> flags = setFlags(header);
            return new TcpDetails(
                header.getSrcPort().valueAsInt(),
                header.getDstPort().valueAsInt(),
                header.getSequenceNumberAsLong(),
                header.getAcknowledgmentNumberAsLong(),
                flags,
                header.getWindowAsInt()


            );
            
            


        }
        else if (ipProtocol == IpProtocol.UDP){
            UdpPacket udp = ipPacket.get(UdpPacket.class);
            UdpPacket.UdpHeader header = udp.getHeader();
            return new UdpDetails(
                header.getSrcPort().valueAsInt(),
                header.getDstPort().valueAsInt(),
                header.getLengthAsInt()
                
            );

            
        }
        else if (ipProtocol == IpProtocol.ICMP) {
            IcmpV4CommonPacket icmp = ipPacket.get(IcmpV4CommonPacket.class);
            IcmpV4CommonPacket.IcmpV4CommonHeader header = icmp.getHeader();

            Integer identifier = null;
            Integer sequenceNumber = null;

            IcmpV4EchoPacket request = ipPacket.get(IcmpV4EchoPacket.class);
            IcmpV4EchoReplyPacket reply = ipPacket.get(IcmpV4EchoReplyPacket.class);

            if (request != null) {
                identifier = request.getHeader().getIdentifierAsInt();
                sequenceNumber = request.getHeader().getSequenceNumberAsInt();
            } else if (reply != null) {
                identifier = reply.getHeader().getIdentifierAsInt();
                sequenceNumber = reply.getHeader().getSequenceNumberAsInt();
            }

            return new IcmpDetails(
                4,
                Byte.toUnsignedInt(header.getType().value()),
                Byte.toUnsignedInt(header.getCode().value()),
                identifier,
                sequenceNumber
            );
        }
        else if (ipProtocol == IpProtocol.ICMPv6) {
            IcmpV6CommonPacket icmp = ipPacket.get(IcmpV6CommonPacket.class);
            IcmpV6CommonPacket.IcmpV6CommonHeader header = icmp.getHeader();

            Integer identifier = null;
            Integer sequenceNumber = null;

            IcmpV6EchoRequestPacket request = ipPacket.get(IcmpV6EchoRequestPacket.class);
            IcmpV6EchoReplyPacket reply = ipPacket.get(IcmpV6EchoReplyPacket.class);

            if (request != null) {
                identifier = request.getHeader().getIdentifierAsInt();
                sequenceNumber = request.getHeader().getSequenceNumberAsInt();
            } else if (reply != null) {
                identifier = reply.getHeader().getIdentifierAsInt();
                sequenceNumber = reply.getHeader().getSequenceNumberAsInt();
            }

            return new IcmpDetails(
                6,
                Byte.toUnsignedInt(header.getType().value()),
                Byte.toUnsignedInt(header.getCode().value()),
                identifier,
                sequenceNumber
            );
        }
        



        return null;
    }

    private static Set<TcpFlag> setFlags(TcpPacket.TcpHeader header){
        Set<TcpFlag> flags = EnumSet.noneOf(TcpFlag.class);
        if (header.getSyn()) flags.add(TcpFlag.SYN);
        if (header.getAck()) flags.add(TcpFlag.ACK);
        if (header.getFin()) flags.add(TcpFlag.FIN);
        if (header.getRst()) flags.add(TcpFlag.RST);
        if (header.getPsh()) flags.add(TcpFlag.PSH);
        if (header.getUrg()) flags.add(TcpFlag.URG);

        return flags;
    }

    private static IpProtocol getProtocolType(String protocolString){
        String protocol = protocolString.toLowerCase();

        if (protocol.equals("tcp")){
            return IpProtocol.TCP;
        }
        if (protocol.equals("udp")){
            return IpProtocol.UDP;
        }
        if (protocol.equals("icmp")){
            return IpProtocol.ICMP;
        }
        if (protocol.equals("icmpv6")){
            return IpProtocol.ICMPv6;
        }

        return null;


    }



    
}
