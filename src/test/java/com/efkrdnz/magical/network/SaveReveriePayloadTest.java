package com.efkrdnz.magical.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SaveReveriePayloadTest {
    @Test
    void aDraftRoundTrips() {
        CompoundTag data = new CompoundTag();
        data.putString("name", "Pit");
        SaveReveriePayload packet = new SaveReveriePayload(2, data);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SaveReveriePayload.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, SaveReveriePayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void aDraftPastTheCapIsRefusedOnTheWayIn() {
        CompoundTag data = new CompoundTag();
        net.minecraft.nbt.ListTag names = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < 40; i++) {
            names.add(net.minecraft.nbt.StringTag.valueOf("x".repeat(2000)));
        }
        data.put("names", names);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SaveReveriePayload.STREAM_CODEC.encode(buffer, new SaveReveriePayload(0, data));
            assertThrows(RuntimeException.class, () -> SaveReveriePayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }
}
