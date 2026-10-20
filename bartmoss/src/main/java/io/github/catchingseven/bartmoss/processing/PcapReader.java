package io.github.catchingseven.bartmoss.processing;
import java.io.EOFException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.Packet;

import io.github.catchingseven.bartmoss.domain.Capture;
import io.github.catchingseven.bartmoss.domain.CapturedPacket;
import io.github.catchingseven.bartmoss.domain.FileFormat;
/*Application calls PcapReader, which uses PacketMapper and returns a Capture.
Application passes that Capture to CaptureJsonSerializer.
Application receives the JSON and coordinates Persistence and Integration.*/
public class PcapReader {
    private PcapMapper mapper;
    

    public Capture readFromFile(String filePath) throws EOFException, TimeoutException, NotOpenException{
        PcapHandle handle;
        Capture capture = new Capture(0, "", null);
        try {
            handle = Pcaps.openOffline(filePath);
            // fill in capture details
            String fileName = extractFileName(filePath);
            capture.setCaptureId(1);
            capture.setSourceFileName(fileName);
            capture.setFileFormat(detectFileFormat(fileName));

           
           try {
            int packetNumber = 0;
            while (true){
                Packet packet = handle.getNextPacketEx(); 
                int capturedLength = packet.length();
                int originalLength = handle.getOriginalLength();
                Instant timestamp = handle.getTimestamp().toInstant();
                Optional<CapturedPacket> capturedPacket = mapper.mapPacket(packet, timestamp, packetNumber, capturedLength, originalLength);

                if (capturedPacket.isPresent()){
                    capture.getPackets().add(capturedPacket.get());
                }
                packetNumber++;
                
                
            }
            
           } finally {
            handle.close();
           }


           
            
        } catch (PcapNativeException e) {
            System.err.println("Unable to open capture: " + e.getMessage());
        }

        
        
        
        return capture;
    }



    /*Helpers us3d to extract the file name and file format for creating Capture object */


    private static String extractFileName(String filePath){
        return Path.of(filePath).getFileName().toString();
    }

    private static FileFormat detectFileFormat(String fileName){
        String name = fileName.toLowerCase();

        if (name.endsWith(".pcapng")){
            return FileFormat.PCAPNG;
        }
        else if (name.endsWith(".pcap")) {
            return FileFormat.PCAPNG;
        }

        return null;

    }



}
