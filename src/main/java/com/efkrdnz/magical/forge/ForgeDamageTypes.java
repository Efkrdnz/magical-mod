package com.efkrdnz.magical.forge;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/** The custom damage type used by forged-weapon combat strikes. */
public final class ForgeDamageTypes {

    public static final ResourceKey<DamageType> FORGE_STRIKE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ForgeIds.id("forge_strike"));

    private ForgeDamageTypes() {}

    /** direct = the strike entity (or the owner when there is none), causing = the wielding player. */
    public static DamageSource strike(ServerLevel level, Entity direct, Entity causing) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(FORGE_STRIKE), direct, causing);
    }

    /**
     * Vanilla {@code minecraft:magic}, credited to the wielder: the damage type the plan asks the
     * element riders, the Arts and PIERCE to use, but with a causing entity on it.
     *
     * <p>{@code damageSources().magic()} carries no entity at all, so nothing it kills ever sets
     * {@code lastHurtByPlayer}: a mob finished off by an Art's splash, a rider's chain or a
     * lingering zone dropped no experience, no looting-scaled loot and gave no kill credit. The
     * type is deliberately unchanged - {@code minecraft:magic} and {@code minecraft:indirect_magic}
     * share {@code bypasses_armor} and {@code bypasses_wolf_armor} but differ on
     * {@code no_knockback}, {@code always_triggers_silverfish} and {@code avoids_guardian_thorns},
     * so swapping to the indirect form would quietly change three behaviours. Only the attribution
     * moves.</p>
     *
     * <p>No direct entity: an Art is not an entity, and leaving it null keeps
     * {@code getWeaponItem()} empty exactly as the strike source does.</p>
     */
    public static DamageSource magic(Entity causing) {
        return new DamageSource(causing.damageSources().magic().typeHolder(), null, causing);
    }
}
