package com.efkrdnz.magical.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The create-skill packet carries its two ids across the wire unchanged. */
class CreateSkillPayloadTest {

    @Test
    void thePairRoundTrips() {
        CreateSkillPayload packet = new CreateSkillPayload(
                ResourceLocation.fromNamespaceAndPath("magical", "wildfire"),
                ResourceLocation.fromNamespaceAndPath("magical", "abyssal_discharge"));
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            CreateSkillPayload.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, CreateSkillPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes(), "bytes left over after decoding");
        } finally {
            buffer.release();
        }
    }

    @Test
    void theTypeIsTheModsOwn() {
        assertEquals("magical:create_skill", CreateSkillPayload.TYPE.id().toString());
    }
}
