package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: open the Spell Creator screen. The class tree sends it after checking the
 * class; the {@code /magical creator} command sends it with a tab and, for captures, the two
 * ingredients to preload. The screen is a plain screen with no menu behind it, so this packet is
 * the whole of the open handshake.
 */
public record OpenSpellCreatorPayload(int tab, Optional<ResourceLocation> first, Optional<ResourceLocation> second)
        implements CustomPacketPayload {
    public static final int TAB_CREATE = 0;
    public static final int TAB_FORMULAS = 1;

    public static final Type<OpenSpellCreatorPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "open_spell_creator"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenSpellCreatorPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    OpenSpellCreatorPayload::tab,
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC),
                    OpenSpellCreatorPayload::first,
                    ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC),
                    OpenSpellCreatorPayload::second,
                    OpenSpellCreatorPayload::new);

    /** The Create tab with nothing loaded: what the class tree opens. */
    public static OpenSpellCreatorPayload create() {
        return new OpenSpellCreatorPayload(TAB_CREATE, Optional.empty(), Optional.empty());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
