package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * One press of the fused blade: the slash, or the blast that ends it.
 *
 * <p>One Blade is the only skill in the kit whose <em>use</em> is a mouse button rather than the
 * ability key, because the ability key is already held down carrying the fusion. So the press
 * cannot arrive through {@code CastLoadoutSlotPayload}, which would be read as a second cast of a
 * skill that is mid-hold, and it needs a packet of its own.
 *
 * <p>One bit, and only one, because everything else is on the server already: which wielder is
 * carrying, how much Edge fused into the blade, how long since the last slash and whether the pool
 * can pay for another. {@code secondary} is the sneak half - the blast - and is the only thing the
 * client knows that the server does not, since a crouch is the client's own input and a right
 * mouse button is not an entity state at all.
 *
 * <p><b>Nothing here validates.</b> {@code OneBladeSkill.slash} and {@code blast} both begin by
 * asking for a formed {@code Fusion} on this wielder and answer false without it, so a forged
 * packet from a client that never fused anything spends nothing and changes nothing. The unlock is
 * re-asked on the way in for the same reason every other {@code playToServer} arm re-asks it.
 */
public record OneBladeStrikePayload(boolean secondary) implements CustomPacketPayload {

    public static final Type<OneBladeStrikePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "one_blade_strike"));

    /** A single byte. There is nothing else in the press that the server has to be told. */
    public static final StreamCodec<RegistryFriendlyByteBuf, OneBladeStrikePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    OneBladeStrikePayload::secondary,
                    OneBladeStrikePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
