package com.efkrdnz.magical.magic.sound;

/**
 * What an instrument does when the Riff plays it. Sixteen instruments, five ways of hurting.
 *
 * <p>Each family is one shape of attack and one colour of note, and the numbers here are its base:
 * {@link RiffNote} scales them by the note's pitch and the Riff's amplitude. A speed of zero means the
 * note does not fly - the kit strikes where it stands and the keys are a ray, both landing the tick
 * they are played.
 */
public enum Family {
    /** A cone of force in front of the wielder, all knockback. */
    DRUMS(0xFF6A3D, 1.1F, 0.0F, 1.0F, 5.0F, 0.9F),
    /** A slow, broad wave that goes through walls and every body in its way, and weighs them down. */
    LOW(0x9B5CFF, 0.9F, 0.45F, 1.4F, 18.0F, 0.15F),
    /** A ray that lands the moment it is played and passes through the first body into the second. */
    KEYS(0x3FD8FF, 1.0F, 0.0F, 0.35F, 24.0F, 0.05F),
    /** A bolt that rings through armour. */
    BELLS(0xFFD23F, 1.2F, 1.1F, 0.5F, 20.0F, 0.3F),
    /** A note that bends toward the nearest enemy in front of it. */
    STRINGS(0x5CFF8A, 0.8F, 0.8F, 0.45F, 22.0F, 0.1F);

    private final int rgb;
    private final float damage;
    private final float speed;
    private final float radius;
    private final float range;
    private final float knockback;

    Family(int rgb, float damage, float speed, float radius, float range, float knockback) {
        this.rgb = rgb;
        this.damage = damage;
        this.speed = speed;
        this.radius = radius;
        this.range = range;
        this.knockback = knockback;
    }

    public int rgb() {
        return rgb;
    }

    public float damage() {
        return damage;
    }

    /** Blocks a tick; zero for a family that lands where it is played. */
    public float speed() {
        return speed;
    }

    public float radius() {
        return radius;
    }

    public float range() {
        return range;
    }

    public float knockback() {
        return knockback;
    }

    public boolean flies() {
        return speed > 0.0F;
    }
}
