package com.efkrdnz.magical.magic.passive;

/**
 * Multipliers a class passive may apply to a cast before it resolves.
 *
 * <p>{@link com.efkrdnz.magical.magic.MagicSkillResolvedStats} is a record, so handlers cannot edit
 * it in place. They fold their contribution into one of these instead and the dispatcher builds the
 * final record once. Multipliers compose, so two passives that both want cheaper spells stack
 * multiplicatively rather than one silently winning.</p>
 */
public final class CastAdjustment {
    public float damage = 1.0F;
    public float size = 1.0F;
    public float mana = 1.0F;
    public float cooldown = 1.0F;
    public float knockback = 1.0F;

    /** Set by a handler that wants the cast to consume a one-shot buff it is holding. */
    public boolean consumed;
}
