package com.efkrdnz.magical.particle;

import com.efkrdnz.magical.forge.visual.MatterKind;
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
 * One piece of forge matter: what it is and the three colours it runs through. By hand it is
 * {@code /particle magical:forge_matter{kind:"ember",hot:16777215,body:16744448,cool:2763306}}; in
 * code the swing and the hit build it from an element's {@code ForgeMatter} table and the strike's
 * palette.
 *
 * @param hot   its colour while it burns, 0xRRGGBB
 * @param body  its colour as it stops burning, or all its life for a kind that never burns
 * @param cool  its colour at the end of its life
 * @param scale a size multiplier over the kind's own size
 */
public record ForgeMatterOptions(ParticleType<ForgeMatterOptions> type, MatterKind kind, int hot, int body, int cool,
        float scale) implements ParticleOptions {

    private static final Codec<MatterKind> KIND_CODEC = Codec.STRING.comapFlatMap(
            name -> MatterKind.byName(name).map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> "Unknown forge matter: " + name)),
            MatterKind::serializedName);

    private static final StreamCodec<ByteBuf, MatterKind> KIND_STREAM = ByteBufCodecs.idMapper(
            ByIdMap.continuous(MatterKind::ordinal, MatterKind.values(), ByIdMap.OutOfBoundsStrategy.ZERO),
            MatterKind::ordinal);

    public ForgeMatterOptions {
        Objects.requireNonNull(kind, "kind");
        scale = Math.max(0.05F, Math.min(TintedParticleOptions.MAX_SCALE, scale));
        hot &= 0xFFFFFF;
        body &= 0xFFFFFF;
        cool &= 0xFFFFFF;
    }

    @Override
    public ParticleType<ForgeMatterOptions> getType() {
        return type;
    }

    public static MapCodec<ForgeMatterOptions> codec(ParticleType<ForgeMatterOptions> type) {
        return RecordCodecBuilder.mapCodec(instance -> instance.group(
                        KIND_CODEC.fieldOf("kind").forGetter(ForgeMatterOptions::kind),
                        ExtraCodecs.RGB_COLOR_CODEC.fieldOf("hot").forGetter(ForgeMatterOptions::hot),
                        ExtraCodecs.RGB_COLOR_CODEC.fieldOf("body").forGetter(ForgeMatterOptions::body),
                        ExtraCodecs.RGB_COLOR_CODEC.fieldOf("cool").forGetter(ForgeMatterOptions::cool),
                        Codec.FLOAT.optionalFieldOf("scale", 1.0F).forGetter(ForgeMatterOptions::scale))
                .apply(instance, (kind, hot, body, cool, scale) -> new ForgeMatterOptions(type, kind, hot, body, cool, scale)));
    }

    public static StreamCodec<RegistryFriendlyByteBuf, ForgeMatterOptions> streamCodec(ParticleType<ForgeMatterOptions> type) {
        return StreamCodec.composite(
                KIND_STREAM, ForgeMatterOptions::kind,
                ByteBufCodecs.INT, ForgeMatterOptions::hot,
                ByteBufCodecs.INT, ForgeMatterOptions::body,
                ByteBufCodecs.INT, ForgeMatterOptions::cool,
                ByteBufCodecs.FLOAT, ForgeMatterOptions::scale,
                (kind, hot, body, cool, scale) -> new ForgeMatterOptions(type, kind, hot, body, cool, scale));
    }
}
