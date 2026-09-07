package com.efkrdnz.magical.forge;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable snapshot of a single strike within a combo, handed to {@link StrikeSpecial} handlers.
 *
 * @param hitIndex       how many bodies this press has touched so far, this one included. {@code 1}
 *                       marks the first body opened up, which is where an Art that may only happen
 *                       once per press - a lingering zone, a chain, a line sweep - hangs itself.
 * @param passHitIndex   how many bodies the current pass has touched, this one included. A pass is
 *                       one flurry pulse or one ring of a heavy slam; single-pass forms never tell
 *                       it apart from {@code hitIndex}. An Art that happens once per <em>pulse</em>
 *                       rather than once per press hangs itself on this.
 * @param targetHitIndex how many times <em>this</em> body has been touched by this press, this touch
 *                       included. Always {@code 1} for a single-hit form; for a flurry it is the
 *                       pulse number, which is what the stacking Arts count.
 * @param targetBefore   what the target was already suffering when the strike reached it, read
 *                       before this press's element rider ran.
 */
public record StrikeContext(ResourceLocation formId, FormFamily family, boolean heavy, int comboIndex,
        boolean finisher, float dealtDamage, Vec3 direction, int hitIndex, int passHitIndex, int targetHitIndex,
        TargetState targetBefore) {

    /**
     * The target's element-relevant condition as it was <em>before</em> this press's rider touched
     * it.
     *
     * <p>The rider runs first by design, and FROST's freezes while FIRE's ignites. An Art that read
     * the live entity would therefore always find its own element's condition already satisfied on
     * its own element's weapon - Rime Split's "frozen targets take half again as much" and Cinder
     * Lance's "+20% against something already burning" would both be unconditional, and the table's
     * conditions would gate nothing. Reading this snapshot instead means the condition can only be
     * met by something that was true before the swing.</p>
     *
     * <p>Free of Minecraft types on purpose, so the rule can be pinned by a plain unit test.</p>
     */
    public record TargetState(boolean burning, boolean frozen) {}

    /** True on the first body this press opened up: the once-per-press gate. */
    public boolean firstBody() {
        return hitIndex == 1;
    }

    /** True on the first body this <em>pass</em> opened up: the once-per-pulse gate. */
    public boolean firstBodyOfPass() {
        return passHitIndex == 1;
    }
}
