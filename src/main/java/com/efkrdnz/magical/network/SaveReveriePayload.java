package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A Daydream draft sent home. The server reads it through {@code ReverieNbt.load} and
 * {@code Reverie.validate}, so a forged packet can at worst describe a smaller scene than it meant to.
 */
public record SaveReveriePayload(int slot, CompoundTag data) implements CustomPacketPayload {
    /** A full draft is a few dozen entries of a few dozen bytes; anything past this is not one. */
    public static final long MAX_BYTES = 64 * 1024;

    public static final Type<SaveReveriePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "save_reverie"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SaveReveriePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.slot());
                buf.writeNbt(payload.data());
            },
            buf -> {
                int slot = buf.readVarInt();
                Tag data = buf.readNbt(NbtAccounter.create(MAX_BYTES));
                return new SaveReveriePayload(slot, data instanceof CompoundTag tag ? tag : new CompoundTag());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
