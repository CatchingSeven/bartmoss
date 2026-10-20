package io.github.catchingseven.bartmoss.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class TcpFlagTest {

    @Test
    void declaresSupportedTcpFlags() {
        assertArrayEquals(
                new TcpFlag[]{TcpFlag.SYN, TcpFlag.ACK, TcpFlag.FIN, TcpFlag.RST,
                        TcpFlag.PSH, TcpFlag.URG, TcpFlag.ECE, TcpFlag.CWR},
                TcpFlag.values());
    }
}
