package com.efkrdnz.magical.entity.fx;

/**
 * Server-side behaviour of a {@link SpellEffectEntity}, registered per skill id in
 * {@link SpellBehaviors}. Stateless: all state lives in the entity's synced data / NBT.
 */
public interface SpellBehavior {
    /** Called once after spawn (server). */
    default void onSpawn(SpellEffectEntity entity) {
    }

    /** Called every server tick while alive. */
    void tick(SpellEffectEntity entity);

    /** Called when life runs out or the entity is discarded through {@link SpellEffectEntity#finish()}. */
    default void onExpire(SpellEffectEntity entity) {
    }

    /** Called after NBT load (chunk reload) so long-lived effects can re-acquire resources. */
    default void onLoad(SpellEffectEntity entity) {
    }
}
