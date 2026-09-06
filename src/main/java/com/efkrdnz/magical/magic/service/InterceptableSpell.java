package com.efkrdnz.magical.magic.service;

import net.minecraft.world.entity.Entity;

/**
 * Marker for mod spell entities that projectile-interception skills (maw, lodestone, satellites,
 * inversion pane, firmament) may eat, bend or reflect. Vanilla {@link net.minecraft.world.entity.projectile.Projectile}s qualify automatically.
 */
public interface InterceptableSpell {
    /** The entity that fired this spell, or null. */
    Entity spellOwner();

    /** Called when the spell is redirected; default implementations only need velocity. */
    default void onRedirected(Entity newOwner) {
    }
}
