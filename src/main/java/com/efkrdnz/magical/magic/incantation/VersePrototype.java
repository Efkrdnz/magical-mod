package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.magic.MagicSchool;
import net.minecraft.resources.ResourceLocation;

/**
 * The body a verse names: the port of the projectile XML. A verse adds its deltas to the shot state
 * and names one of these; the spawner adds the two together. A static one does not move and pulses
 * its effect every {@code pulseIntervalTicks} for {@code durationTicks}.
 *
 * <p>{@code pulse} is what a standing body does to everything inside it on a beat; {@code hit} is
 * what a body of its own does to what it strikes, and it lives here rather than in the shot state
 * because it is the body's, the way {@code fireball.xml} carries its own burn. A Wreath writes into
 * the state and so reaches every body after it; an Ember burns because an Ember is on fire.
 *
 * <p>{@code matter} and {@code shape} are what a material body puts in the world and how; null and
 * {@link MatterShape#NONE} on every other body. They are the prototype's for the same reason the
 * burn is: a Sea of Water is water because it is a sea, not because a verse before it said so.
 */
public record VersePrototype(ResourceLocation id, MagicSchool school, double damage, double healing, double speed,
                             int lifetimeTicks, float radius, boolean isStatic, int durationTicks,
                             double explosionRadius, double explosionDamage, HitEffect pulse, HitEffect hit,
                             int pulseIntervalTicks, Look look, boolean carriesCaster, Matter matter, MatterShape shape) {

    public enum Look {
        NEEDLE, ORB, SHARD, EMBER, ARC, DART, WHISPER, BLINK, RING, BURST, PIT, WORD
    }

    public VersePrototype {
        if ((shape == MatterShape.NONE) == (matter != null)) {
            throw new IllegalArgumentException(id + " names " + (matter == null ? "no matter" : matter) + " with the shape " + shape);
        }
    }

    public boolean explodes() {
        return explosionRadius > 0.0D;
    }

    /** True for a body whose work is a block in the world. */
    public boolean laysMatter() {
        return shape != MatterShape.NONE;
    }
}
