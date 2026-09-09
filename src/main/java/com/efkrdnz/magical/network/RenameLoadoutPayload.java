package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.MagicLoadout;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * A request to rename one loadout.
 *
 * <p>The only free text a client sends this mod, so it is bounded twice: the codec refuses a string
 * longer than the field allows before it is even decoded, and
 * {@link MagicLoadout#sanitizeName(String)} strips control characters and colour codes when it
 * lands. The cap here is generous relative to the real limit so a slightly long name is trimmed
 * rather than dropping the packet.
 */
public record RenameLoadoutPayload(int index, String name) implements CustomPacketPayload {
    public static final Type<RenameLoadoutPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "rename_loadout"));

    private static final int MAX_WIRE_LENGTH = MagicLoadout.MAX_NAME_LENGTH * 4;

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameLoadoutPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RenameLoadoutPayload::index,
                    ByteBufCodecs.stringUtf8(MAX_WIRE_LENGTH), RenameLoadoutPayload::name,
                    RenameLoadoutPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
