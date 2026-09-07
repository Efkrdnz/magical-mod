package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.forge.chain.ForgeError;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Server reply to a {@link ForgeSubmitPayload}.
 *
 * @param success  whether the forge succeeded
 * @param error    the reason it was refused, empty on success
 * @param argument message context: a glyph index, a slot count, a mana/second amount, or a quality
 * @param quality  final weapon quality on success, 0 otherwise
 */
public record ForgeResultPayload(boolean success, Optional<ForgeError> error, int argument, int quality)
        implements CustomPacketPayload {
    public static final Type<ForgeResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "forge_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgeResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, ForgeResultPayload::success,
            ByteBufCodecs.optional(NeoForgeStreamCodecs.enumCodec(ForgeError.class)), ForgeResultPayload::error,
            ByteBufCodecs.VAR_INT, ForgeResultPayload::argument,
            ByteBufCodecs.VAR_INT, ForgeResultPayload::quality,
            ForgeResultPayload::new);

    public static ForgeResultPayload ok(int quality) {
        return new ForgeResultPayload(true, Optional.empty(), 0, quality);
    }

    public static ForgeResultPayload fail(ForgeError error, int argument) {
        return new ForgeResultPayload(false, Optional.of(error), argument, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
