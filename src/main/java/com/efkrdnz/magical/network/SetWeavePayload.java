package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A whole board as the editor left it, in the same tag {@code Weave.save()} writes.
 *
 * <p>A tag rather than a flattened list of pins because the board already has a save format and a
 * second one would be a second thing to keep in step. Nothing here is trusted: the server loads it
 * into a scratch Weave, which drops words it does not know, clamps every number, refuses a wire
 * that runs the wrong way or closes a loop, and only then is the budget checked and the result
 * copied over. A forged packet can at worst describe a smaller board than it meant to.
 */
public record SetWeavePayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<SetWeavePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "set_weave"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetWeavePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeNbt(payload.data()),
            buf -> new SetWeavePayload(buf.readNbt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
