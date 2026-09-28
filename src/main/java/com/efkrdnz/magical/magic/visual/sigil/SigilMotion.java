package com.efkrdnz.magical.magic.visual.sigil;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * How a sigil moves and fades. Gravity is vanilla's particle field, spent as {@code 0.04 * gravity}
 * of a block a tick, so a negative one lifts; friction is what it keeps of its speed each tick.
 * The two curves run over the particle's life, {@code t} from 0 to 1.
 */
public enum SigilMotion {
    /** Lifts, slows, holds, fades, shrinks a little: the rune particle's motion. The default. */
    RISE(0.90F, -0.1F, false, 18, 10, 0.0F),
    /** Stays where it is written, then fades: a mark that has to hold its shape. */
    HOVER(0.60F, 0.0F, false, 44, 8, 0.0F),
    /** Carries the velocity it is given, barely slowing: a burst, a throw. */
    DRIFT(0.96F, 0.0F, false, 24, 8, 0.0F),
    /** Sinks and sways, and settles on what it lands on: a thing shed, a curse falling. */
    FALL(0.92F, 0.25F, true, 30, 10, 0.006F);

    private final float friction;
    private final float gravity;
    private final boolean physics;
    private final int life;
    private final int lifeJitter;
    private final float sway;

    SigilMotion(float friction, float gravity, boolean physics, int life, int lifeJitter, float sway) {
        this.friction = friction;
        this.gravity = gravity;
        this.physics = physics;
        this.life = life;
        this.lifeJitter = lifeJitter;
        this.sway = sway;
    }

    public float friction() {
        return friction;
    }

    public float gravity() {
        return gravity;
    }

    public boolean physics() {
        return physics;
    }

    /** The shortest life in ticks; each particle adds up to {@link #lifeJitter()} more. */
    public int life() {
        return life;
    }

    public int lifeJitter() {
        return lifeJitter;
    }

    /** Blocks a tick of sideways push a falling sigil sways by; zero for the rest. */
    public float sway() {
        return sway;
    }

    /** Opacity over its life: whole, then gone by the end. */
    public float alpha(float t) {
        float holds = switch (this) {
            case RISE, DRIFT -> 0.6F;
            case HOVER -> 0.7F;
            case FALL -> 0.65F;
        };
        float u = Math.max(0.0F, Math.min(1.0F, t));
        return u < holds ? 1.0F : 1.0F - (u - holds) / (1.0F - holds);
    }

    /** Size over its life, as a factor of the size it was sent at. */
    public float size(float t) {
        float u = Math.max(0.0F, Math.min(1.0F, t));
        return switch (this) {
            case RISE -> 1.0F - 0.3F * u;
            case HOVER -> 1.0F;
            case DRIFT -> 1.0F - 0.15F * u;
            case FALL -> 1.0F - 0.2F * u;
        };
    }

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SigilMotion> byName(String name) {
        return Arrays.stream(values()).filter(motion -> motion.serializedName().equals(name)).findFirst();
    }
}
