package com.yeowool.federation.chat;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TargetedPayloadTest {

    @Test
    void encodesCountThenUuidsThenMessageInProxyReadOrder() throws IOException {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        byte[] payload = TargetedPayload.encode(List.of(first, second), "{\"text\":\"안녕\"}");

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        assertEquals(2, in.readInt());
        assertEquals(first.toString(), in.readUTF());
        assertEquals(second.toString(), in.readUTF());
        assertEquals("{\"text\":\"안녕\"}", in.readUTF());
        assertEquals(0, in.available());
    }
}
