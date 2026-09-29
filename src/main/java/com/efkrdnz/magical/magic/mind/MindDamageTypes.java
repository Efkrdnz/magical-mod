package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;

/**
 * The harm a believed lie does. It is vanilla {@code indirect_magic} in every respect - the same
 * death messages, exhaustion and scaling, and every vanilla tag that type is in - and one more:
 * {@code #minecraft:no_knockback}. Imagined lava has nowhere to push from, and indirect_magic would
 * push its victim away from the wielder, who is standing somewhere else entirely, and tilt the hurt
 * camera toward them.
 */
public final class MindDamageTypes {

    public static final ResourceKey<DamageType> PHANTOM_HARM = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "phantom_harm"));

    private MindDamageTypes() {
    }

    /** {@code direct} is the figment that bit, or null; {@code causing} is the wielder when online, or null. */
    public static DamageSource phantom(ServerLevel level, Entity direct, Entity causing) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(PHANTOM_HARM),
                direct, causing);
    }
}
