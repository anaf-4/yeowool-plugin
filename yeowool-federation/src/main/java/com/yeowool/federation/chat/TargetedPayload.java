package com.yeowool.federation.chat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collection;
import java.util.UUID;

/** Byte layout of the proxy's {@code yeowool:targeted} channel — must match YeowoolProxy#deliverTargeted's read order. */
public final class TargetedPayload {

    private TargetedPayload() {
    }

    public static byte[] encode(Collection<UUID> recipients, String componentJson) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(recipients.size());
            for (UUID recipient : recipients) {
                out.writeUTF(recipient.toString());
            }
            out.writeUTF(componentJson);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }
}
