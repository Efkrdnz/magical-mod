package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's own particles, all four textured sprites tinted by the colour they are spawned in.
 *
 * <p>They exist for the half of a spell that is matter rather than light. The FX library draws
 * light - additive quads that glow and clip toward white - and was being asked to draw the debris
 * too: a glass splinter, an ember, a rune lifting off a circle, a puff of coloured smoke. A
 * textured particle on vanilla's own sheet is lit by the world, occludes and fades by alpha, which
 * is what matter does. Vanilla's own particles cover the elements that have them (flame, splash,
 * snowflake, smoke); these four cover what it has no sprite for in a colour it does not come in.
 */
public final class MagicalParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, MagicalMod.MODID);

    /** A small rune glyph that lifts and fades, full bright: the arcane schools' matter. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<TintedParticleOptions>> RUNE = tinted("rune");

    /** A tumbling fragment that falls and settles, lit by the world: ice, glass, crystal, bone. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<TintedParticleOptions>> SHARD = tinted("shard");

    /** A four-pointed twinkle, full bright, drifting: sparks of light where vanilla has none in the colour. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<TintedParticleOptions>> MOTE = tinted("mote");

    /** A soft puff of coloured smoke that rises, spreads and thins: mist, miasma, a void's breath. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<TintedParticleOptions>> WISP = tinted("wisp");

    private MagicalParticles() {
    }

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }

    private static DeferredHolder<ParticleType<?>, ParticleType<TintedParticleOptions>> tinted(String name) {
        return PARTICLES.register(name, () -> new ParticleType<TintedParticleOptions>(false) {
            private final MapCodec<TintedParticleOptions> codec = TintedParticleOptions.codec(this);
            private final StreamCodec<RegistryFriendlyByteBuf, TintedParticleOptions> streamCodec =
                    TintedParticleOptions.streamCodec(this);

            @Override
            public MapCodec<TintedParticleOptions> codec() {
                return codec;
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, TintedParticleOptions> streamCodec() {
                return streamCodec;
            }
        });
    }
}
