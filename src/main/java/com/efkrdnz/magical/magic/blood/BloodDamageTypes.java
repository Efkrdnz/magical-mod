package com.efkrdnz.magical.magic.blood;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/**
 * The price blood magic charges against the body.
 *
 * <p>Its own type rather than {@code minecraft:magic} for two reasons. The bypass tags take it past
 * armour, resistance, enchantments and the hurt cooldown, so the number a skill asks for is the
 * number the caster loses; and {@code PassiveHooks.isSpellDamage} and
 * {@code MagicSinService.isMagicDamage} both hard-check {@code DamageTypes.MAGIC}, so a price no
 * longer feeds Wrath, Gluttony or anything that keys off being hit by a spell. Paying your own bill
 * is not being attacked.
 *
 * <p>The barrier is not vanilla and knows nothing about tags, so
 * {@code MagicGameplayEvents.onIncomingDamage} returns early on this type before it reaches Mana
 * Skin, the sin soak or {@code absorbDamage}. The two halves together are what "true damage" means
 * here.
 *
 * <p>Deliberately not tagged {@code bypasses_invulnerability}: creative and genuinely invulnerable
 * players are already handled by {@code MagicPrice.waived}, so the tag would buy nothing and could
 * kill someone the game says cannot be killed.
 */
public final class BloodDamageTypes {

    public static final ResourceKey<DamageType> BLOOD_PRICE = ResourceKey.create(Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "blood_price"));

    private BloodDamageTypes() {
    }

    /** No direct entity and no attacker: the caster is both, and neither of them is an assailant. */
    public static DamageSource price(LivingEntity payer) {
        return new DamageSource(payer.level().registryAccess()
                .lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BLOOD_PRICE));
    }
}
