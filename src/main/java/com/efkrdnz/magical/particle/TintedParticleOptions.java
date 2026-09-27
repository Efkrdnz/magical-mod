package com.efkrdnz.magical.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;

/**
 * One of the mod's own particles in one colour at one size: the four sprites are drawn pale so the
 * colour a spell spawns them in is the only hue they have (see {@code scripts/particle-sprites.py}).
 *
 * @param rgb   the tint, 0xRRGGBB
 * @param scale a size multiplier on the sprite's own quad; 1 is a vanilla-sized particle
 */
public record TintedParticleOptions(ParticleType<TintedParticleOptions> type, int rgb, float scale)
        implements ParticleOptions {

    /** Beyond this a tinted sprite is a smear rather than a particle, and a hand-edited command is refused. */
    public static final float MAX_SCALE = 8.0F;

    public TintedParticleOptions {
        scale = Math.max(0.05F, Math.min(MAX_SCALE, scale));
        rgb &= 0xFFFFFF;
    }

    @Override
    public ParticleType<TintedParticleOptions> getType() {
        return type;
    }

    public float red() {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    public float green() {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    public float blue() {
        return (rgb & 0xFF) / 255.0F;
    }

    public static MapCodec<TintedParticleOptions> codec(ParticleType<TintedParticleOptions> type) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                        ExtraCodecs.RGB_COLOR_CODEC.fieldOf("color").forGetter(TintedParticleOptions::rgb),
                        Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(TintedParticleOptions::scale))
                .apply(instance, (rgb, scale) -> new TintedParticleOptions(type, rgb, scale)));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, TintedParticleOptions> streamCodec(
            ParticleType<TintedParticleOptions> type) {
        return StreamCodec.composite(
                ByteBufCodecs.INT, TintedParticleOptions::rgb,
                ByteBufCodecs.FLOAT, TintedParticleOptions::scale,
                (rgb, scale) -> new TintedParticleOptions(type, rgb, scale));
    }
}
