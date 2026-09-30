package com.efkrdnz.magical.magic.sound;

/**
 * One note of a Riff, resolved into numbers: what it costs and what it does.
 *
 * <p>Two dials and one table. The {@link Family} gives the base. The pitch is the wavelength: a low
 * note is a long wave, wide and slow and far-reaching and heavy, and a high note is a short one,
 * narrow and fast and precise and sharper on what it hits. The amplitude is the energy: damage, size
 * and knockback grow with it in a line, and the mana grows with its square, so the loudest riff is
 * five times the damage of the quietest for twenty-five times the price.
 */
public record RiffNote(Instrument instrument, int pitch, int amplitude,
                       float damage, float mana, float speed, float radius, float range, float knockback) {

    public static final float BASE_DAMAGE = 1.6F;
    public static final float MANA_PER_ENERGY = 0.4F;

    public static RiffNote resolve(Instrument instrument, int pitch, int amplitude) {
        Family family = instrument.family();
        int clampedPitch = Math.max(0, Math.min(Riff.PITCHES - 1, pitch));
        int amp = Riff.clampAmplitude(amplitude);
        float high = clampedPitch / (float) (Riff.PITCHES - 1);
        float damage = BASE_DAMAGE * amp * family.damage() * (0.85F + 0.3F * high);
        float mana = MANA_PER_ENERGY * amp * amp;
        float speed = family.speed() * (0.7F + 0.8F * high);
        float radius = family.radius() * (1.3F - 0.6F * high) * (0.8F + 0.1F * amp);
        float range = family.range() * (1.2F - 0.4F * high);
        float knockback = family.knockback() * (1.3F - 0.6F * high) * (0.6F + 0.2F * amp);
        return new RiffNote(instrument, clampedPitch, amp, damage, mana, speed, radius, range, knockback);
    }

    public Family family() {
        return instrument.family();
    }

    /** Ticks a flying note lives before it has run its range out. */
    public int lifeTicks() {
        return speed <= 0.0F ? 1 : Math.max(1, (int) Math.ceil(range / speed));
    }
}
