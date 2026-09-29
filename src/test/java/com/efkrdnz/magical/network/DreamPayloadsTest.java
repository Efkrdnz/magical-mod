package com.efkrdnz.magical.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DreamPayloadsTest {
    private static <T> void roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T packet) {
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
    void anEditRoundTrips() {
        roundTrip(DreamEditPayload.STREAM_CODEC, new DreamEditPayload(DreamEditPayload.PLACE,
                List.of(new BlockPos(1_000_000, 100, 1_000_001), new BlockPos(1_000_001, 100, 1_000_001)), "block:minecraft:stone", -1));
        roundTrip(DreamEditPayload.STREAM_CODEC, new DreamEditPayload(DreamEditPayload.FLAW, List.of(), "", 42));
    }

    @Test
    void anEditCarriesNoMoreThanABrushFull() {
        List<BlockPos> cells = new ArrayList<>();
        for (int i = 0; i <= DreamEditPayload.MAX_CELLS; i++) {
            cells.add(new BlockPos(i, 0, 0));
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            assertThrows(Exception.class, () -> DreamEditPayload.STREAM_CODEC.encode(buffer, new DreamEditPayload(0, cells, "", -1)));
        } finally {
            buffer.release();
        }
    }

    @Test
    void aStateRoundTrips() {
        roundTrip(DreamStatePayload.STREAM_CODEC, new DreamStatePayload(true, Optional.of(new BlockPos(1_000_001, 101, 1_000_000)), -1));
        roundTrip(DreamStatePayload.STREAM_CODEC, new DreamStatePayload(true, Optional.empty(), 17));
        roundTrip(DreamStatePayload.STREAM_CODEC, new DreamStatePayload(false, Optional.empty(), -1));
    }
}
