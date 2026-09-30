package com.efkrdnz.magical.magic.sound;

/**
 * How fast the Song runs, as game ticks per sixteenth-note step.
 *
 * <p>A tick is the finest clock a server has, so a tempo is an integer number of them: two, three and
 * four ticks a step are 150, 100 and 75 beats a minute. Slower is easier to act on and costs the same
 * upkeep a bar, which is the trade: a brisk Song hands out more chances a minute and asks for a
 * steadier hand to take them.
 */
public enum Tempo {
    BRISK(2),
    STEADY(3),
    SLOW(4);

    private final int ticksPerStep;

    Tempo(int ticksPerStep) {
        this.ticksPerStep = ticksPerStep;
    }

    public int ticksPerStep() {
        return ticksPerStep;
    }

    /** Quarter-note beats a minute: four steps to the beat, twenty ticks to the second. */
    public int bpm() {
        return 60 * 20 / (ticksPerStep * 4);
    }

    /** One pass of the whole score. */
    public int loopTicks() {
        return Score.STEPS * ticksPerStep;
    }

    /** One bar, the unit the upkeep is charged in. */
    public int barTicks() {
        return Score.BAR * ticksPerStep;
    }

    public Tempo next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static Tempo byName(String name) {
        for (Tempo tempo : values()) {
            if (tempo.name().equalsIgnoreCase(name)) {
                return tempo;
            }
        }
        return STEADY;
    }
}
