package com.efkrdnz.magical.magic.sword;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/**
 * What an over-stretched Array charges its wielder, and it is the same shape as the blood price.
 *
 * <p>Sword God's rung removes the rule that the draw may not be exceeded, and the bill for that is
 * the wielder's own body: 1.0 every 40 ticks for every full 20 points of strain. A number that
 * armour, resistance, a passive or the barrier could take a slice out of would be a number the
 * apex quietly stops paying the moment it is geared, which is exactly the wrong direction for the
 * one cost that is supposed to grow with the power. So this rides in the same four bypass tags
 * {@code magical:blood_price} does and {@code MagicGameplayEvents.onIncomingDamage} returns early
 * on it before Mana Skin, the sin soak or {@code absorbDamage} is reached. The two halves together
 * are what "true damage" means here, and one without the other is a half-charged bill.
 *
 * <p>Its own type rather than {@code minecraft:magic} for the second reason the blood price has
 * one: {@code PassiveHooks.isSpellDamage} and {@code MagicSinService.isMagicDamage} both hard-check
 * {@code DamageTypes.MAGIC}, so carrying your own Array no longer feeds Wrath or anything else
 * that keys off having been attacked. Straining yourself is not being attacked.
 *
 * <p>Deliberately not tagged {@code bypasses_invulnerability}, for the blood price's reason: a
 * creative or genuinely invulnerable player is already excused, and the tag could kill somebody
 * the game says cannot be killed.
 */
public final class SwordDamageTypes {

    public static final ResourceKey<DamageType> SWORD_STRAIN = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "sword_strain"));

    private SwordDamageTypes() {
    }

    /** No direct entity and no attacker: the Array is the wielder's, and neither is an assailant. */
    public static DamageSource strain(LivingEntity payer) {
        return new DamageSource(payer.level().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(SWORD_STRAIN));
    }
}
