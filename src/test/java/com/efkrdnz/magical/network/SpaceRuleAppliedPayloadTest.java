package com.efkrdnz.magical.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** The rule-applied packet survives the wire: three ordinals in, the same three out, no bytes left. */
class SpaceRuleAppliedPayloadTest {

    private static void roundTrip(SpaceRuleAppliedPayload packet) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SpaceRuleAppliedPayload.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, SpaceRuleAppliedPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes(), "bytes left over after decoding");
        } finally {
            buffer.release();
        }
    }

    @Test
    void theThreeOrdinalsRoundTrip() {
        roundTrip(new SpaceRuleAppliedPayload(0, 2, 1));
        roundTrip(new SpaceRuleAppliedPayload(11, 56, 4));
    }

    @Test
    void theTypeIsTheModsOwn() {
        assertEquals("magical:space_rule_applied", SpaceRuleAppliedPayload.TYPE.id().toString());
    }
}
