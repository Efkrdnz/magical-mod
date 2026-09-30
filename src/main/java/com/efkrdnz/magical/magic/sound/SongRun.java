package com.efkrdnz.magical.magic.sound;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * One performance of a Song: the score it plays, the tick its first step fell on, and the streak.
 *
 * <p>Time is the only input. Step {@code n} of the performance (counting across loops) falls on
 * {@code start + n * ticksPerStep}; an action is judged against the nearest step, and it is on the
 * beat when that step holds a note and the action lands within {@link #WINDOW} ticks of it. Every
 * track holding a note on that step answers: a crouch on a step where the kick and the melody both
 * sound is a Bulwark and a Mend at once, which is the reward for writing them together.
 *
 * <p>A step can be taken once per action, or crouching on the spot would farm a single kick. An
 * action off the beat breaks the streak; doing nothing never does, because a rest is part of music.
 * The streak climbs to {@link #MAX_STREAK}, where the enemies in earshot are made to dance for a bar
 * and the streak starts again.
 *
 * <p>Two ways in. {@link #act} judges an action at a tick, which is how the server judges one it saw
 * for itself; {@link #claim} takes the step a client says it acted on and only checks that the claim
 * is possible, because the client heard the music it acted to and the server did not.
 */
public final class SongRun {

    /** Ticks either side of a note that still count as on it. */
    public static final int WINDOW = 2;
    /** On-beat actions in a row that make the enemies dance. */
    public static final int MAX_STREAK = 8;
    /** How far a client claim may sit from the server clock and still be believed. */
    public static final int LAG_TOLERANCE = 10;

    /** The step nearest a moment, and how far from it the moment was. */
    public record Beat(long index, double offset) {
        public int step() {
            return (int) Math.floorMod(index, (long) Score.STEPS);
        }

        public long loop() {
            return Math.floorDiv(index, (long) Score.STEPS);
        }
    }

    public enum Kind {
        ON_BEAT,
        OFF_BEAT,
        ALREADY_TAKEN,
        NOT_BELIEVED
    }

    /** The answer to one action: which tracks it landed on, the streak after it, and a dance. */
    public record Judgement(Kind kind, Set<Track> tracks, int streak, boolean dance) {
        public boolean onBeat() {
            return kind == Kind.ON_BEAT;
        }
    }

    private final Score score;
    private final long start;
    private final Map<Long, Integer> taken = new HashMap<>();
    private int streak;

    public SongRun(Score score, long start) {
        this.score = score;
        this.start = start;
    }

    public Score score() {
        return score;
    }

    public long start() {
        return start;
    }

    public int streak() {
        return streak;
    }

    public static Beat nearest(long start, Tempo tempo, double tick) {
        double relative = (tick - start) / tempo.ticksPerStep();
        long index = Math.round(relative);
        return new Beat(index, (relative - index) * tempo.ticksPerStep());
    }

    /** The tick step {@code index} of a performance falls on. */
    public static long tickOf(long start, Tempo tempo, long index) {
        return start + index * tempo.ticksPerStep();
    }

    /** The step playing at a tick: the last one started at or before it. */
    public static long indexAt(long start, Tempo tempo, long tick) {
        return Math.floorDiv(tick - start, (long) tempo.ticksPerStep());
    }

    public Beat nearest(double tick) {
        return nearest(start, score.tempo(), tick);
    }

    /**
     * The note an action at this tick lands on: the nearest step within {@link #WINDOW} that holds a
     * note, or -1. Nearest note, not nearest step - on a three-tick grid an action two ticks after a
     * kick is nearer the empty step after it, and a rest must never steal a hit from a note.
     */
    public long noteNear(double tick) {
        int tps = score.tempo().ticksPerStep();
        long around = (long) Math.floor((tick - start) / tps);
        long best = -1L;
        double bestDistance = Double.MAX_VALUE;
        for (long index = around - 1; index <= around + 2; index++) {
            double distance = Math.abs(tick - tickOf(start, score.tempo(), index));
            if (index >= 0 && distance <= WINDOW && distance < bestDistance
                    && score.sounds((int) Math.floorMod(index, (long) Score.STEPS))) {
                best = index;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Whether an action at this tick would land on a note, without judging it. */
    public boolean wouldLand(double tick) {
        return noteNear(tick) >= 0;
    }

    /** Judge an action seen at this tick. */
    public Judgement act(SongAction action, double tick) {
        long index = noteNear(tick);
        if (index < 0) {
            return miss();
        }
        return land(action, index, (long) Math.floor(tick));
    }

    /** Take a step a client says it acted on, believed only if the server clock allows it. */
    public Judgement claim(SongAction action, long index, long now) {
        long at = tickOf(start, score.tempo(), index);
        if (index < 0 || Math.abs(at - now) > LAG_TOLERANCE || !score.sounds((int) Math.floorMod(index, (long) Score.STEPS))) {
            return new Judgement(Kind.NOT_BELIEVED, Set.of(), streak, false);
        }
        return land(action, index, now);
    }

    /** An action off the beat: the streak is gone. */
    public Judgement miss() {
        streak = 0;
        return new Judgement(Kind.OFF_BEAT, Set.of(), 0, false);
    }

    private Judgement land(SongAction action, long index, long now) {
        forgetBefore(indexAt(start, score.tempo(), now) - Score.STEPS);
        int bit = 1 << action.ordinal();
        int already = taken.getOrDefault(index, 0);
        if ((already & bit) != 0) {
            return new Judgement(Kind.ALREADY_TAKEN, Set.of(), streak, false);
        }
        taken.put(index, already | bit);
        EnumSet<Track> tracks = EnumSet.noneOf(Track.class);
        int step = (int) Math.floorMod(index, (long) Score.STEPS);
        for (Track track : Track.values()) {
            if (score.sounds(track, step)) {
                tracks.add(track);
            }
        }
        streak++;
        boolean dance = streak >= MAX_STREAK;
        int shown = streak;
        if (dance) {
            streak = 0;
        }
        return new Judgement(Kind.ON_BEAT, tracks, shown, dance);
    }

    private void forgetBefore(long index) {
        taken.keySet().removeIf(key -> key < index);
    }
}
