package com.efkrdnz.magical.magic.incantation;

import java.util.Locale;

/**
 * What one press lands on a single target that every body meets: struck by every flying body,
 * standing in every static for the whole of its life.
 *
 * <p>A flying body lands its hit and its healing, then the explosion its end carries at full
 * strength (a hit ends it on the target), then whatever a Latch releases there. A static lands a
 * pulse per interval over its duration, the explosion its end carries, and whatever a Fuse or an
 * Epitaph releases where it stood. A Fuse on a flying body goes off wherever the body happens to
 * be and an Epitaph on one wherever it expired, so neither is counted; nor is a crit, which is a
 * roll, nor a bounce burst, which is a wall, nor falloff, which is where the target stands. It is
 * the most one target can take from the press. Pure, so the Grimoire and the preview command read
 * the same number without a level.
 */
public record Landing(double damage, double healing) {

    public static final Landing NOTHING = new Landing(0.0D, 0.0D);

    public boolean isNothing() {
        return damage <= 0.0D && healing <= 0.0D;
    }

    public static Landing of(ShotPlan shot) {
        double[] sums = new double[2];
        for (ProjectilePlan body : shot.bodies()) {
            add(body, sums);
        }
        return new Landing(sums[0], sums[1]);
    }

    private static void add(ProjectilePlan body, double[] sums) {
        VersePrototype prototype = body.prototype();
        ShotState stamped = body.stamped();
        double damage = stamped.nullsDamage() ? 0.0D : Math.max(0.0D, prototype.damage() + stamped.damageAdd());
        double healing = Math.max(0.0D, prototype.healing() + stamped.healingAdd());
        int times = prototype.isStatic() ? pulses(prototype) : 1;
        sums[0] += damage * times;
        sums[1] += healing * times;
        double explosionRadius = Math.max(0.0D, prototype.explosionRadius() + stamped.explosionRadius());
        if (explosionRadius > 0.0D && !stamped.nullsDamage()) {
            sums[0] += Math.max(0.0D, prototype.explosionDamage() + stamped.explosionDamageAdd());
        }
        if (body.hasPayload() && releasesAtTheTarget(prototype, body.payloadKind())) {
            for (ProjectilePlan child : body.payload().bodies()) {
                add(child, sums);
            }
        }
    }

    /** A flying body releases on the target only by its hit; a static, standing on it, by either clock. */
    static boolean releasesAtTheTarget(VersePrototype prototype, PayloadKind kind) {
        return prototype.isStatic() ? kind == PayloadKind.FUSE || kind == PayloadKind.EPITAPH : kind == PayloadKind.LATCH;
    }

    /** The pulses a static gets in: one per interval over its duration, the way its tick counts them. */
    static int pulses(VersePrototype prototype) {
        int interval = Math.max(1, prototype.pulseIntervalTicks());
        return Math.max(1, prototype.durationTicks()) / interval;
    }

    /** A number as the reading prints it: whole where it is whole, else to a tenth. */
    public static String amount(double value) {
        double tenths = Math.round(value * 10.0D) / 10.0D;
        if (tenths == Math.rint(tenths)) {
            return Long.toString((long) tenths);
        }
        return String.format(Locale.ROOT, "%.1f", tenths);
    }
}
