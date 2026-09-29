package com.efkrdnz.magical.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SetBeltSlotPayloadTest {
    private static void roundTrip(SetBeltSlotPayload packet) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SetBeltSlotPayload.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, SetBeltSlotPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes(), "bytes left over after decoding");
        } finally {
            buffer.release();
        }
    }

    @Test
    void aSlotAndAKeyRoundTrip() {
        roundTrip(new SetBeltSlotPayload(4, "block:minecraft:stone"));
    }

    @Test
    void anEmptyKeyClearsAndRoundTrips() {
        roundTrip(new SetBeltSlotPayload(0, ""));
    }
}
