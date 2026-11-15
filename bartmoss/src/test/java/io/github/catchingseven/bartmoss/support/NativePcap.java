package io.github.catchingseven.bartmoss.support;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.pcap4j.core.Pcaps;

public final class NativePcap {
    private NativePcap() { }

    public static void requireLibrary() {
        try {
            Pcaps.libVersion();
        } catch (UnsatisfiedLinkError | NoClassDefFoundError error) {
            assumeTrue(false, "Native PCAP tests require Npcap/libpcap: " + error.getMessage());
        }
    }
}
