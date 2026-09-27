package com.efkrdnz.magical.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.magic.sword.ImpactWave;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * The one packet a sword hit is carried in.
 *
 * <p>It goes to every player near the hit, up to a dozen times a volley, so what is pinned besides
 * the round trip is that it stays small: a hit is a point, a normal, a radius, a kind and the
 * struck block, and nothing else rides along.
 */
class SwordImpactPayloadTest {

    private static void roundTrip(SwordImpactPayload packet) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SwordImpactPayload.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, SwordImpactPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes(), "bytes left over after decoding");
        } finally {
            buffer.release();
        }
    }

    @Test
    void aBladeIntoABodyRoundTrips() {
        roundTrip(new SwordImpactPayload(100.25D, 64.5D, -31.75D, 0.0F, 0.6F, -0.8F, 0.7F,
                ImpactWave.Kind.CUT.ordinal(), 0));
        assertEquals("magical:sword_impact", SwordImpactPayload.TYPE.id().toString());
    }

    @Test
    void everyKindAndARealBlockRoundTrip() {
        for (ImpactWave.Kind kind : ImpactWave.Kind.values()) {
            roundTrip(new SwordImpactPayload(-5.0D, 70.0D, 12.0D, 0.0F, 1.0F, 0.0F, 1.6F, kind.ordinal(), 27912));
        }
    }

    @Test
    void aHitIsASmallPacket() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SwordImpactPayload.STREAM_CODEC.encode(buffer, new SwordImpactPayload(
                    1.0D, 2.0D, 3.0D, 0.0F, 1.0F, 0.0F, 0.7F, ImpactWave.Kind.CLANG.ordinal(), 27912));
            assertTrue(buffer.readableBytes() <= 48,
                    "one sword hit costs " + buffer.readableBytes() + " bytes on the wire");
        } finally {
            buffer.release();
        }
    }
}
