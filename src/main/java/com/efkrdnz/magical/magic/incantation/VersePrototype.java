package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.MagicSchool;
import net.minecraft.resources.ResourceLocation;

/**
 * The body a verse names: the port of the projectile XML. A verse adds its deltas to the shot state
 * and names one of these; the spawner adds the two together. A static one does not move and pulses
 * its effect every {@code pulseIntervalTicks} for {@code durationTicks}.
 */
public record VersePrototype(ResourceLocation id, MagicSchool school, double damage, double healing, double speed,
                             int lifetimeTicks, float radius, boolean isStatic, int durationTicks,
                             double explosionRadius, double explosionDamage, HitEffect pulse, int pulseIntervalTicks,
                             Look look, boolean carriesCaster) {

    public enum Look {
        NEEDLE, ORB, SHARD, EMBER, ARC, DART, WHISPER, BLINK, RING, BURST, PIT, WORD
    }

    public boolean explodes() {
        return explosionRadius > 0.0D;
    }
}
