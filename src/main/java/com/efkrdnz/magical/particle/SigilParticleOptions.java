package com.efkrdnz.magical.particle;

import com.efkrdnz.magical.magic.visual.sigil.Sigil;
import com.efkrdnz.magical.magic.visual.sigil.SigilMotion;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.ExtraCodecs;

/**
 * One sigil: a symbol, the two colours of its ink, a size and a way of moving. By hand it is
 * {@code /particle magical:sigil{sigil:"eye",core:16774864,glow:12086784,motion:"hover"}}; in code
 * it is made by {@code Sigils} from a {@code SigilMark}.
 *
 * @param core  the strokes' colour, 0xRRGGBB
 * @param glow  the halo's colour, 0xRRGGBB
 * @param scale a size multiplier; 1 is a vanilla-sized particle
 */
public record SigilParticleOptions(ParticleType<SigilParticleOptions> type, Sigil sigil, int core, int glow, float scale,
        SigilMotion motion) implements ParticleOptions {

    private static final Codec<Sigil> SIGIL_CODEC = Codec.STRING.comapFlatMap(
            name -> Sigil.byName(name).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown sigil: " + name)),
            Sigil::serializedName);

    private static final Codec<SigilMotion> MOTION_CODEC = Codec.STRING.comapFlatMap(
            name -> SigilMotion.byName(name).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown sigil motion: " + name)),
            SigilMotion::serializedName);

    private static final StreamCodec<ByteBuf, Sigil> SIGIL_STREAM = ByteBufCodecs.idMapper(
            ByIdMap.continuous(Sigil::ordinal, Sigil.values(), ByIdMap.OutOfBoundsStrategy.ZERO), Sigil::ordinal);

    private static final StreamCodec<ByteBuf, SigilMotion> MOTION_STREAM = ByteBufCodecs.idMapper(
            ByIdMap.continuous(SigilMotion::ordinal, SigilMotion.values(), ByIdMap.OutOfBoundsStrategy.ZERO), SigilMotion::ordinal);

    public SigilParticleOptions {
        Objects.requireNonNull(sigil, "sigil");
        Objects.requireNonNull(motion, "motion");
        scale = Math.max(0.05F, Math.min(TintedParticleOptions.MAX_SCALE, scale));
        core &= 0xFFFFFF;
        glow &= 0xFFFFFF;
    }

    @Override
    public ParticleType<SigilParticleOptions> getType() {
        return type;
    }

    public static MapCodec<SigilParticleOptions> codec(ParticleType<SigilParticleOptions> type) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                        SIGIL_CODEC.fieldOf("sigil").forGetter(SigilParticleOptions::sigil),
                        ExtraCodecs.RGB_COLOR_CODEC.fieldOf("core").forGetter(SigilParticleOptions::core),
                        ExtraCodecs.RGB_COLOR_CODEC.fieldOf("glow").forGetter(SigilParticleOptions::glow),
                        Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(SigilParticleOptions::scale),
                        MOTION_CODEC.optionalFieldOf("motion", SigilMotion.RISE).forGetter(SigilParticleOptions::motion))
                .apply(instance, (sigil, core, glow, scale, motion) -> new SigilParticleOptions(type, sigil, core, glow, scale, motion)));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, SigilParticleOptions> streamCodec(ParticleType<SigilParticleOptions> type) {
        return StreamCodec.composite(
                SIGIL_STREAM, SigilParticleOptions::sigil,
                ByteBufCodecs.INT, SigilParticleOptions::core,
                ByteBufCodecs.INT, SigilParticleOptions::glow,
                ByteBufCodecs.FLOAT, SigilParticleOptions::scale,
                MOTION_STREAM, SigilParticleOptions::motion,
                (sigil, core, glow, scale, motion) -> new SigilParticleOptions(type, sigil, core, glow, scale, motion));
    }
}
