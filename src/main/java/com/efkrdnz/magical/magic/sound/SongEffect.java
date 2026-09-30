package com.efkrdnz.magical.magic.sound;

/**
 * What an action on a note of a track does. A crouch keeps the gift for the wielder; a swing throws
 * it at every enemy in earshot. Each track gives its own pair, so a score written heavy on the kit is
 * a defensive song and one written heavy on the melody is a healing one.
 *
 * <p>The strength of every effect follows the streak, the run of on-beat actions not broken by an
 * off-beat one: {@link #potency} scales the numbers, {@link #amplifier} steps the vanilla effect up a
 * level once the run is half way to full.
 */
public enum SongEffect {
    /** The kit, crouched on: every blow taken is lighter. */
    BULWARK(Track.PERCUSSION, SongAction.CROUCH),
    /** The kit, swung on: every enemy in earshot is jolted back and stopped for a moment. */
    STAGGER(Track.PERCUSSION, SongAction.SWING),
    /** The bass, crouched on: nothing knocks the wielder off their feet. */
    ROOTED(Track.BASS, SongAction.CROUCH),
    /** The bass, swung on: every enemy in earshot is made heavy. */
    WEIGHT(Track.BASS, SongAction.SWING),
    /** The melody, crouched on: the wielder heals. */
    MEND(Track.MELODY, SongAction.CROUCH),
    /** The melody, swung on: every enemy in earshot hits softer. */
    DISSONANCE(Track.MELODY, SongAction.SWING);

    /** The streak at which an effect steps up a level. */
    public static final int STRONG_STREAK = 4;

    private final Track track;
    private final SongAction action;

    SongEffect(Track track, SongAction action) {
        this.track = track;
        this.action = action;
    }

    public Track track() {
        return track;
    }

    public SongAction action() {
        return action;
    }

    /** A crouch keeps the effect; a swing sends it out. */
    public boolean onWielder() {
        return action == SongAction.CROUCH;
    }

    public static SongEffect of(Track track, SongAction action) {
        for (SongEffect effect : values()) {
            if (effect.track == track && effect.action == action) {
                return effect;
            }
        }
        throw new IllegalArgumentException(track + " " + action);
    }

    /** 1 at no streak, 2 at a full one. */
    public static float potency(int streak) {
        return 1.0F + Math.max(0, Math.min(SongRun.MAX_STREAK, streak)) / (float) SongRun.MAX_STREAK;
    }

    public static int amplifier(int streak) {
        return streak >= STRONG_STREAK ? 1 : 0;
    }

    /** Health a Mend gives back: one heart at a full streak. */
    public static float mend(int streak) {
        return 1.0F * potency(streak);
    }

    /** How hard a Stagger shoves, in blocks a tick. */
    public static float stagger(int streak) {
        return 0.35F * potency(streak);
    }
}
