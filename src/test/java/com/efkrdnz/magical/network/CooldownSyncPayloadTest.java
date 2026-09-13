package com.efkrdnz.magical.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** The cooldown packet survives the wire in both directions and refuses a hostile length. */
class CooldownSyncPayloadTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("magical", path);
    }

    private static void roundTrip(CooldownSyncPayload packet) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            CooldownSyncPayload.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, CooldownSyncPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes(), "bytes left over after decoding");
        } finally {
            buffer.release();
        }
    }

    @Test
    void anEmptyReplaceAllRoundTrips() {
        roundTrip(new CooldownSyncPayload(true, List.of()));
    }

    @Test
    void aSingleCastRoundTrips() {
        roundTrip(new CooldownSyncPayload(false, List.of(new CooldownSyncPayload.Entry(id("arcane_snap"), 60, 80))));
    }

    @Test
    void aSubSkillCastCarriesTheParentAndAClearRoundTrips() {
        roundTrip(new CooldownSyncPayload(false, List.of(
                new CooldownSyncPayload.Entry(id("gabriel_judgement"), 4200, 4200),
                new CooldownSyncPayload.Entry(id("gabriel"), 80, 80),
                new CooldownSyncPayload.Entry(id("black_flames"), 0, 0))));
    }

    @Test
    void moreEntriesThanTheRosterHasSkillsIsRefused() {
        List<CooldownSyncPayload.Entry> entries = new ArrayList<>();
        for (int i = 0; i <= CooldownSyncPayload.MAX_ENTRIES; i++) {
            entries.add(new CooldownSyncPayload.Entry(id("skill_" + i), 1, 1));
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            assertThrows(RuntimeException.class, () -> CooldownSyncPayload.STREAM_CODEC.encode(buffer, new CooldownSyncPayload(true, entries)));
        } finally {
            buffer.release();
        }
    }
}
