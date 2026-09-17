package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One writ, chosen on the four wheels and released.
 *
 * <p>{@code target} is a skill id, or {@code #SCHOOL} for a writ binding a whole school. The client
 * only ever offers what the sender has witnessed, and the server checks that again before writing
 * anything: a forged packet naming a spell its sender has never seen is refused, not obeyed.
 */
public record ApplyWritPayload(String target, int aspect, int operation, int subject) implements CustomPacketPayload {
    public static final Type<ApplyWritPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "apply_writ"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ApplyWritPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(256),
                    ApplyWritPayload::target,
                    ByteBufCodecs.INT,
                    ApplyWritPayload::aspect,
                    ByteBufCodecs.INT,
                    ApplyWritPayload::operation,
                    ByteBufCodecs.INT,
                    ApplyWritPayload::subject,
                    ApplyWritPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
