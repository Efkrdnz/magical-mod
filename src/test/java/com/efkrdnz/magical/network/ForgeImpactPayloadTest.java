package com.efkrdnz.magical.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.efkrdnz.magical.forge.ForgeElementKind;
import com.efkrdnz.magical.forge.visual.ForgeImpactForm;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

/**
 * The impact packet, both ways across the wire. A colour written as a signed medium comes back
 * negative, and an ordinal read as a signed byte turns the last elements into nonsense - both draw
 * the wrong thing and fail nothing.
 */
class ForgeImpactPayloadTest {

    @Test
    void anImpactComesBackExactlyAsItWasSent() {
        ForgeImpactPayload sent = new ForgeImpactPayload(1.5D, -64.25D, 30000.125D, 0.0F, 1.0F, 0.0F, 0.35F,
                ForgeImpactForm.BLOCK.ordinal(), ForgeElementKind.values().length - 1, 5,
                ForgeImpactPayload.HEAVY | ForgeImpactPayload.ECHO, 1234, 0xFFEEDD, 0x802010, 0xFFFFFF);
        assertEquals(sent, roundTrip(sent));
    }

    @Test
    void aColourKeepsOnlyItsThreeBytes() {
        ForgeImpactPayload sent = new ForgeImpactPayload(0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0,
                0xFF123456, 0x80FFFFFF, -1);
        assertEquals(0x123456, sent.primary());
        assertEquals(0xFFFFFF, roundTrip(sent).edge());
    }

    @Test
    void theFlagsReadAsTheyWereSet() {
        ForgeImpactPayload heavy = new ForgeImpactPayload(0, 0, 0, 0, 1, 0, 0, 0, 0, 0,
                ForgeImpactPayload.HEAVY, 0, 0, 0, 0);
        assertTrue(heavy.heavy());
        assertFalse(heavy.echo());
    }

    @Test
    void anUnknownFormIsReadAsAHit() {
        assertEquals(ForgeImpactForm.HIT, ForgeImpactForm.byOrdinal(200));
        assertEquals(ForgeImpactForm.HIT, ForgeImpactForm.byOrdinal(-1));
    }

    private static ForgeImpactPayload roundTrip(ForgeImpactPayload sent) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ForgeImpactPayload.STREAM_CODEC.encode(buf, sent);
        return ForgeImpactPayload.STREAM_CODEC.decode(buf);
    }
}
