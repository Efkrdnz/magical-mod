package com.efkrdnz.magical.forge;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.efkrdnz.magical.forge.chain.ForgeGrade;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

public record ForgedWeapon(ResourceLocation element, ForgeGrade grade, Optional<ResourceLocation> temper,
        List<ResourceLocation> forms, List<ResourceLocation> modifiers, int quality, long forgedAtGameTime) {

    private static final Codec<ForgeGrade> GRADE_CODEC = Codec.STRING.comapFlatMap(
            s -> ForgeGrade.byName(s).map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown forge grade: " + s)),
            ForgeGrade::serializedName);

    public static final Codec<ForgedWeapon> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("element").forGetter(ForgedWeapon::element),
            GRADE_CODEC.fieldOf("grade").forGetter(ForgedWeapon::grade),
            ResourceLocation.CODEC.optionalFieldOf("temper").forGetter(ForgedWeapon::temper),
            ResourceLocation.CODEC.listOf().fieldOf("forms").forGetter(ForgedWeapon::forms),
            ResourceLocation.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(ForgedWeapon::modifiers),
            Codec.INT.fieldOf("quality").forGetter(ForgedWeapon::quality),
            Codec.LONG.optionalFieldOf("forged_at", 0L).forGetter(ForgedWeapon::forgedAtGameTime)
    ).apply(instance, ForgedWeapon::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgedWeapon> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ForgedWeapon::element,
            NeoForgeStreamCodecs.enumCodec(ForgeGrade.class), ForgedWeapon::grade,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), ForgedWeapon::temper,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(8)), ForgedWeapon::forms,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(8)), ForgedWeapon::modifiers,
            ByteBufCodecs.VAR_INT, ForgedWeapon::quality,
            ByteBufCodecs.VAR_LONG, ForgedWeapon::forgedAtGameTime,
            ForgedWeapon::new);

    public ForgedWeapon {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(grade, "grade");
        Objects.requireNonNull(temper, "temper");
        Objects.requireNonNull(forms, "forms");
        Objects.requireNonNull(modifiers, "modifiers");
        forms = List.copyOf(forms);
        modifiers = List.copyOf(modifiers);
        quality = Mth.clamp(quality, 0, 100);
    }

    public ForgedWeapon withForgedAt(long gameTime) {
        return new ForgedWeapon(element, grade, temper, forms, modifiers, quality, gameTime);
    }
}
