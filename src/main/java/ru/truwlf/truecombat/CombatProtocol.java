package ru.truwlf.truecombat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;

final class CombatProtocol {
    static final String CHANNEL = "truecombat:state";

    private CombatProtocol() {
    }

    static byte[] state(UUID player, boolean active, long remainingSeconds) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(25);
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeLong(player.getMostSignificantBits());
            output.writeLong(player.getLeastSignificantBits());
            output.writeBoolean(active);
            output.writeLong(Math.max(0L, remainingSeconds));
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to encode combat state", exception);
        }
        return bytes.toByteArray();
    }

    static UUID player(byte[] data) {
        if (data == null || data.length < 16) return null;
        ByteBuffer buffer = ByteBuffer.wrap(data);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    static boolean active(byte[] data) {
        return data != null && data.length >= 17 && data[16] != 0;
    }

    static long remainingSeconds(byte[] data) {
        if (data == null || data.length < 25) return 0L;
        return Math.max(0L, ByteBuffer.wrap(data, 17, 8).getLong());
    }
}
