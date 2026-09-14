package com.efkrdnz.magical.client.renderer.eldritch;

/**
 * The motion the code owns, as pure functions of a float age and the synced state.
 *
 * <p>Chain space: the chain stands along {@code up} from its base and bends toward
 * {@code forward}, which is the model's front (the -Z face of the geometry). A pitch is the bend
 * of one segment relative to the one below it; positive bends toward the front. The renderer
 * turns the whole construct to face its target and writes these pitches into the segment bones.
 */
public final class EldritchPose {
    public static final int SEGMENTS = 6;
    /** How far one joint may bend, in radians. A tentacle curls; it does not fold in half. */
    public static final float MAX_BEND = 2.0F;
    public static final float SWAY_RATE = 0.12F;
    public static final int CCD_PASSES = 10;
    public static final float SNAP_TICKS = 3.0F;
    /** The jaws' full gape, in radians. */
    public static final float MAX_GAPE = 0.9F;

    private static final float SEED_CURL = 0.10F;
    private static final float CHATTER = 0.12F;
    private static final float CHATTER_RATE = 1.4F;
    private static final float CHATTER_FADE = 0.06F;
    private static final float SWAY_ON_HOLD = 0.25F;

    private EldritchPose() {
    }

    public static void chain(int segments, float length, float forward, float up, boolean hasTarget, float age, float sway, float[] outPitch) {
        float swayNow = sway * (float) Math.sin(age * SWAY_RATE);
        if (!hasTarget) {
            for (int i = 0; i < segments; i++) {
                outPitch[i] = swayNow * (i + 1) / segments;
            }
            return;
        }
        // Start pointed straight at the target: base bent to the aim, the rest straight.
        float aim = clampBend((float) Math.atan2(forward, up));
        outPitch[0] = aim;
        for (int i = 1; i < segments; i++) {
            outPitch[i] = 0.0F;
        }
        float distance = (float) Math.hypot(forward, up);
        if (distance < segments * length) {
            // Within reach: cyclic coordinate descent from the tip, so the tip lands on the target
            // and the bend spreads down the chain.
            // A straight chain pointed at the target is a dead point for the descent: every joint
            // already sees the tip and the target along one line, so no pass moves anything and
            // the tip overshoots. Start from a slight curl toward the target instead.
            float curl = SEED_CURL * (aim >= 0.0F ? 1.0F : -1.0F);
            for (int i = 1; i < segments; i++) {
                outPitch[i] = curl;
            }
            float[] joint = new float[2];
            float[] tip = new float[2];
            for (int pass = 0; pass < CCD_PASSES; pass++) {
                for (int i = segments - 1; i >= 0; i--) {
                    tip(length, outPitch, i, joint);
                    tip(length, outPitch, segments, tip);
                    float toTip = (float) Math.atan2(tip[0] - joint[0], tip[1] - joint[1]);
                    float toTarget = (float) Math.atan2(forward - joint[0], up - joint[1]);
                    outPitch[i] = clampBend(outPitch[i] + wrap(toTarget - toTip));
                }
            }
        }
        for (int i = 0; i < segments; i++) {
            outPitch[i] = clampBend(outPitch[i] + swayNow * SWAY_ON_HOLD * (i + 1) / segments);
        }
    }

    /** Forward kinematics: where the chain is after {@code count} segments, as (forward, up). */
    public static void tip(float length, float[] pitch, int count, float[] out) {
        float heading = 0.0F;
        float forward = 0.0F;
        float up = 0.0F;
        for (int i = 0; i < count; i++) {
            heading += pitch[i];
            forward += length * (float) Math.sin(heading);
            up += length * (float) Math.cos(heading);
        }
        out[0] = forward;
        out[1] = up;
    }

    public static float grown(float age, float formTicks, int segments) {
        if (formTicks <= 0.0F) {
            return segments;
        }
        return segments * Math.max(0.0F, Math.min(1.0F, age / formTicks));
    }

    public static float segmentScale(float grown, int index) {
        return Math.max(0.0F, Math.min(1.0F, grown - index));
    }

    public static float dissolve(float age, float life, float ticks) {
        if (life <= 0.0F || ticks <= 0.0F) {
            return 1.0F;
        }
        float left = life - age;
        return Math.max(0.0F, Math.min(1.0F, left / ticks));
    }

    public static float ease(float current, float wanted, float lag) {
        return current + wrap(wanted - current) * lag;
    }

    public static float jaws(float age, float windup, float snapAt) {
        if (snapAt <= 0.0F || age < snapAt) {
            return windup <= 0.0F ? 1.0F : Math.max(0.0F, Math.min(1.0F, age / windup));
        }
        float since = age - snapAt;
        if (since < SNAP_TICKS) {
            return 1.0F - since / SNAP_TICKS;
        }
        float after = since - SNAP_TICKS;
        return CHATTER * Math.abs((float) Math.sin(after * CHATTER_RATE)) * (float) Math.exp(-after * CHATTER_FADE);
    }

    public static float pupil(boolean fixed, float age) {
        float breath = 0.05F * (float) Math.sin(age * 0.08F);
        return (fixed ? 0.6F : 1.0F) + breath;
    }

    private static float clampBend(float pitch) {
        return Math.max(-MAX_BEND, Math.min(MAX_BEND, pitch));
    }

    /** An angle difference brought into -pi..pi. */
    private static float wrap(float angle) {
        float twoPi = (float) (Math.PI * 2.0D);
        angle %= twoPi;
        if (angle > Math.PI) {
            angle -= twoPi;
        } else if (angle < -Math.PI) {
            angle += twoPi;
        }
        return angle;
    }
}
