package com.efkrdnz.magical.forge;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.efkrdnz.magical.forge.chain.ForgeChainCompiler;
import com.efkrdnz.magical.forge.chain.ForgeGrade;
import com.efkrdnz.magical.forge.chain.ForgeProgram;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * The component a forged weapon carries.
 *
 * <p>{@code program} is the run of forms and modifiers in the order they were drawn, and is what
 * the weapon actually fires. {@code forms} and {@code modifiers} are the flat view kept beside it:
 * the tooltip, the cost totals and the kept-rune strip all read them, and a weapon forged before
 * programs existed has only those. An empty {@code program} means exactly that, and
 * {@link com.efkrdnz.magical.forge.chain.ForgeChainCompiler#fromLegacy} rebuilds the old behaviour
 * from the flat lists rather than re-rolling the weapon.
 */
public record ForgedWeapon(ResourceLocation element, ForgeGrade grade, Optional<ResourceLocation> temper,
        List<ResourceLocation> forms, List<ResourceLocation> modifiers, List<ResourceLocation> program,
        int quality, long forgedAtGameTime) {

    /**
     * The wire cap on every rune list. It has to be at least the glyph budget a single chain can
     * spend, or a top-grade weapon would be silently truncated on its way to the client.
     */
    private static final int MAX_RUNES = 16;

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
            ResourceLocation.CODEC.listOf().optionalFieldOf("program", List.of()).forGetter(ForgedWeapon::program),
            Codec.INT.fieldOf("quality").forGetter(ForgedWeapon::quality),
            Codec.LONG.optionalFieldOf("forged_at", 0L).forGetter(ForgedWeapon::forgedAtGameTime)
    ).apply(instance, ForgedWeapon::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, ForgedWeapon> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ForgedWeapon::element,
            NeoForgeStreamCodecs.enumCodec(ForgeGrade.class), ForgedWeapon::grade,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), ForgedWeapon::temper,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_RUNES)), ForgedWeapon::forms,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_RUNES)), ForgedWeapon::modifiers,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_RUNES)), ForgedWeapon::program,
            ByteBufCodecs.VAR_INT, ForgedWeapon::quality,
            ByteBufCodecs.VAR_LONG, ForgedWeapon::forgedAtGameTime,
            ForgedWeapon::new);

    public ForgedWeapon {
        Objects.requireNonNull(element, "element");
        Objects.requireNonNull(grade, "grade");
        Objects.requireNonNull(temper, "temper");
        Objects.requireNonNull(forms, "forms");
        Objects.requireNonNull(modifiers, "modifiers");
        Objects.requireNonNull(program, "program");
        forms = List.copyOf(forms);
        modifiers = List.copyOf(modifiers);
        program = List.copyOf(program);
        quality = Mth.clamp(quality, 0, 100);
    }

    public ForgedWeapon withForgedAt(long gameTime) {
        return new ForgedWeapon(element, grade, temper, forms, modifiers, program, quality, gameTime);
    }

    /**
     * The chain as this weapon fires it. Falls back to the flat lists for a weapon forged before
     * programs existed, which reproduces its old behaviour exactly.
     */
    public ForgeProgram compiled() {
        return program.isEmpty()
                ? ForgeChainCompiler.fromLegacy(paths(forms), paths(modifiers))
                : ForgeChainCompiler.compile(paths(program));
    }

    private static List<String> paths(List<ResourceLocation> ids) {
        List<String> out = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            out.add(id.getPath());
        }
        return out;
    }
}
