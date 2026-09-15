package com.efkrdnz.magical.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/**
 * The two packets the pact screen is opened and closed with.
 *
 * <p>The seal packet is the one place a client hands the server a list of ids, so what is pinned
 * here is not only that it survives the wire but that it refuses to carry an unbounded one.
 */
class BloodSacrificePayloadTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("magical", path);
    }

    private static <T> void roundTrip(net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T> codec,
            T packet) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            codec.encode(buffer, packet);
            assertEquals(packet, codec.decode(buffer));
            assertEquals(0, buffer.readableBytes(), "bytes left over after decoding");
        } finally {
            buffer.release();
        }
    }

    @Test
    void theOpenPacketCarriesBothClocks() {
        roundTrip(OpenBloodSacrificePayload.STREAM_CODEC, new OpenBloodSacrificePayload(1200, 1800));
        assertEquals("magical:open_blood_sacrifice", OpenBloodSacrificePayload.TYPE.id().toString());
    }

    @Test
    void anEmptyPactStillRoundTrips() {
        // The screen can send one; the server answers with the refusal line rather than a crash.
        roundTrip(BloodSacrificeSealPayload.STREAM_CODEC, new BloodSacrificeSealPayload(List.of(), List.of()));
    }

    @Test
    void aPactOfBoonsAndPricesRoundTrips() {
        roundTrip(BloodSacrificeSealPayload.STREAM_CODEC, new BloodSacrificeSealPayload(
                List.of(id("crimson_edge"), id("second_heart")),
                List.of(id("thin_skin"), id("glass_bones"), id("the_unknown"))));
        assertEquals("magical:blood_sacrifice_seal", BloodSacrificeSealPayload.TYPE.id().toString());
    }

    @Test
    void theListsAreCappedSoAForgedPacketCannotAllocateForever() {
        List<ResourceLocation> flood = new ArrayList<>();
        for (int i = 0; i <= BloodSacrificeSealPayload.MAX_ENTRIES; i++) {
            flood.add(id("boon_" + i));
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            assertThrows(Exception.class, () -> BloodSacrificeSealPayload.STREAM_CODEC.encode(buffer,
                    new BloodSacrificeSealPayload(flood, List.of())));
        } finally {
            buffer.release();
        }
    }

    @Test
    void theCapIsRoomForTheWholeCatalogueAndNoMore() {
        // Nine points of boon at one point each is the widest legal pact; the catalogue is the
        // hard ceiling above it, so nothing a player can legitimately choose is ever refused here.
        assertTrue(BloodSacrificeSealPayload.MAX_ENTRIES >= 16,
                "every boon at once must still fit, or a legal pact would be dropped as a forgery");
    }
}
