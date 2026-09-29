package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** A scene's belief rows: a viewer is sent its own, the scene's owner everybody's. Belief in hundredths, and which of its elements are real. */
public record BeliefSyncPayload(int scene, List<Entry> entries, List<Integer> manifested) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 512;
    public static final int MAX_MANIFESTED = 128;

    public record Entry(int viewer, int element, byte belief, boolean shattered) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::viewer,
                ByteBufCodecs.VAR_INT, Entry::element,
                ByteBufCodecs.BYTE, Entry::belief,
                ByteBufCodecs.BOOL, Entry::shattered,
                Entry::new);

        public float value() {
            return belief / 100.0F;
        }
    }

    public static final Type<BeliefSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "belief_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BeliefSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BeliefSyncPayload::scene,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), BeliefSyncPayload::entries,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_MANIFESTED)), BeliefSyncPayload::manifested,
            BeliefSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
