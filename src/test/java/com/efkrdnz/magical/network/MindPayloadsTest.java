package com.efkrdnz.magical.network;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MindPayloadsTest {
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
    void aSceneRoundTrips() {
        roundTrip(IllusionScenePayload.STREAM_CODEC, new IllusionScenePayload(7, true,
                List.of(new IllusionScenePayload.Cell(new BlockPos(1, -60, 3), 12, 0),
                        new IllusionScenePayload.Cell(new BlockPos(1, -59, 3), 12, 0))));
    }

    @Test
    void anEndAndABeliefRoundTrip() {
        roundTrip(IllusionEndPayload.STREAM_CODEC, new IllusionEndPayload(7));
        roundTrip(BeliefSyncPayload.STREAM_CODEC, new BeliefSyncPayload(7,
                List.of(new BeliefSyncPayload.Entry(42, 0, (byte) 64, false), new BeliefSyncPayload.Entry(42, 1, (byte) 0, true))));
    }

    @Test
    void aHostileLengthIsRefused() {
        List<IllusionScenePayload.Cell> cells = new ArrayList<>();
        for (int i = 0; i <= IllusionScenePayload.MAX_CELLS; i++) {
            cells.add(new IllusionScenePayload.Cell(BlockPos.ZERO, 1, 0));
        }
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            assertThrows(Exception.class, () -> IllusionScenePayload.STREAM_CODEC.encode(buffer, new IllusionScenePayload(1, false, cells)));
        } finally {
            buffer.release();
        }
    }
}
