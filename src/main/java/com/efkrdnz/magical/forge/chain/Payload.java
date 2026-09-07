package com.efkrdnz.magical.forge.chain;

/**
 * A step nested inside another, fired by the carrier rather than by a press of its own.
 *
 * @param kind       what makes it fire
 * @param delayTicks ticks after spawn for {@link TriggerKind#TIMER}; ignored by the other kinds
 * @param step       what fires
 */
public record Payload(TriggerKind kind, int delayTicks, ForgeStep step) {

    /** The timer's default fuse, halved by each extra stack of the rune. */
    public static final int BASE_TIMER_TICKS = 8;

    public static Payload onImpact(ForgeStep step) {
        return new Payload(TriggerKind.IMPACT, 0, step);
    }

    public static Payload onExpiry(ForgeStep step) {
        return new Payload(TriggerKind.EXPIRY, 0, step);
    }

    public static Payload onTimer(int delayTicks, ForgeStep step) {
        return new Payload(TriggerKind.TIMER, Math.max(1, delayTicks), step);
    }
}
