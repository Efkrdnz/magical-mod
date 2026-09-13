package com.efkrdnz.magical.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.Optional;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The open-creator packet survives the wire with and without its preloaded inputs. */
class OpenSpellCreatorPayloadTest {

    private static void roundTrip(OpenSpellCreatorPayload packet) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            OpenSpellCreatorPayload.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, OpenSpellCreatorPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes(), "bytes left over after decoding");
        } finally {
            buffer.release();
        }
    }

    @Test
    void anEmptyOpenRoundTrips() {
        OpenSpellCreatorPayload plain = OpenSpellCreatorPayload.create();
        assertEquals(OpenSpellCreatorPayload.TAB_CREATE, plain.tab());
        assertTrue(plain.first().isEmpty());
        assertTrue(plain.second().isEmpty());
        roundTrip(plain);
        roundTrip(new OpenSpellCreatorPayload(OpenSpellCreatorPayload.TAB_FORMULAS, Optional.empty(), Optional.empty()));
    }

    @Test
    void preloadedInputsRoundTrip() {
        roundTrip(new OpenSpellCreatorPayload(OpenSpellCreatorPayload.TAB_CREATE,
                Optional.of(ResourceLocation.fromNamespaceAndPath("magical", "wildfire")),
                Optional.of(ResourceLocation.fromNamespaceAndPath("magical", "abyssal_discharge"))));
        roundTrip(new OpenSpellCreatorPayload(OpenSpellCreatorPayload.TAB_CREATE,
                Optional.of(ResourceLocation.fromNamespaceAndPath("magical", "glint")),
                Optional.empty()));
    }

    @Test
    void theTypeIsTheModsOwn() {
        assertEquals("magical:open_spell_creator", OpenSpellCreatorPayload.TYPE.id().toString());
    }
}
