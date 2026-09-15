package com.efkrdnz.magical.magic.passive;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What the sixteen boons of a Blood Sacrifice actually do.
 *
 * <p>Split from {@link SacrificeCurses} because between them they are more than a file's worth, and
 * the two halves get read for different reasons: this one when a boon feels weak, that one when a
 * price feels unfair.
 *
 * <p>Nothing here checks a clock. A boon is an ordinary passive while it lasts, so
 * {@link ClassPassiveEffects#on} is the whole gate and {@code PlayerMagicState.tickRitualPassives}
 * is what takes it away.
 */
final class SacrificeBoons {

    static final float MIGHT = 1.20F;
    static final double EDGE = 0.20D;
    static final double PULSE = 0.15D;
    static final float THINNED_MANA = 0.65F;
    static final float HIDE = 0.85F;
    static final float TIDE = 1.25F;
    static final float SCENT = 1.18F;
    /** Blood Scent only smells blood: anything above this share of its health is not bleeding yet. */
    static final float SCENT_BELOW = 0.40F;
    static final int SECOND_HEART_BARRIER = 40;
    static final float HAEMOPHAGE_SHARE = 0.10F;
    /** Two hearts a cast. Uncapped, one wide area spell would refill the whole bar. */
    static final float HAEMOPHAGE_CAP = 4.0F;
    static final float RACING_COOLDOWN = 0.70F;
    static final float FURY_PER_PRICE = 0.08F;
    static final int VESSEL_SIPHON_BLOOD = 12;
    static final double UNFEELING_KNOCKBACK = 0.6D;
    static final float UNFEELING_STATUS = 0.60F;
    static final double REACH_ENTITY = 1.5D;
    static final double REACH_BLOCK = 1.0D;
    static final double FOOTING_STEP = 0.6D;
    static final double FOOTING_SAFE_FALL = 3.0D;

    private static final ResourceLocation EDGE_MODIFIER = modifier("crimson_edge");
    private static final ResourceLocation PULSE_SPEED_MODIFIER = modifier("quickened_pulse_speed");
    private static final ResourceLocation PULSE_SWING_MODIFIER = modifier("quickened_pulse_swing");
    private static final ResourceLocation REACH_ENTITY_MODIFIER = modifier("long_reach_entity");
    private static final ResourceLocation REACH_BLOCK_MODIFIER = modifier("long_reach_block");
    private static final ResourceLocation UNFEELING_MODIFIER = modifier("unfeeling");
    private static final ResourceLocation FOOTING_STEP_MODIFIER = modifier("sure_footing_step");
    private static final ResourceLocation FOOTING_FALL_MODIFIER = modifier("sure_footing_fall");

    private SacrificeBoons() {
    }

    private static ResourceLocation modifier(String name) {
        return ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "sacrifice/" + name);
    }

    /** Spell damage the player is about to deal, with every damage boon folded in. */
    static float outgoing(PlayerMagicState state, LivingEntity target, float amount) {
        float scaled = amount;
        if (ClassPassiveEffects.on(state, MagicPassiveContent.SANGUINE_MIGHT.id())) {
            scaled *= MIGHT;
        }
        if (ClassPassiveEffects.on(state, MagicPassiveContent.BLOOD_SCENT.id())
                && target != null && target.getMaxHealth() > 0.0F
                && target.getHealth() / target.getMaxHealth() < SCENT_BELOW) {
            scaled *= SCENT;
        }
        return scaled * furyMultiplier(state);
    }

    /**
     * Bloodborne Fury: eight percent per ritual price, and a boon is never a price.
     *
     * <p>The one boon that pays for a heavy pact, and what makes a Hellbroker a build rather than a
     * discount - four prices is a third more damage, bought with everything biting twice as hard.
     */
    static float furyMultiplier(PlayerMagicState state) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.BLOODBORNE_FURY.id())) {
            return 1.0F;
        }
        return 1.0F + FURY_PER_PRICE * state.activeRitualPriceCount();
    }

    /** Damage about to land on the player, softened by Clotted Hide. */
    static float incoming(PlayerMagicState state, float amount) {
        return ClassPassiveEffects.on(state, MagicPassiveContent.CLOTTED_HIDE.id()) ? amount * HIDE : amount;
    }

    /** Thinned Blood, Scarlet Tide and Racing Heart, folded into the cast about to resolve. */
    static void adjustCast(PlayerMagicState state, CastAdjustment out) {
        if (ClassPassiveEffects.on(state, MagicPassiveContent.THINNED_BLOOD.id())) {
            out.mana *= THINNED_MANA;
        }
        if (ClassPassiveEffects.on(state, MagicPassiveContent.SCARLET_TIDE.id())) {
            out.size *= TIDE;
        }
        if (ClassPassiveEffects.on(state, MagicPassiveContent.RACING_HEART.id())) {
            out.cooldown *= RACING_COOLDOWN;
        }
    }

    /** Haemophage drinks its share of what the player's spell just dealt. */
    static void drink(ServerPlayer player, PlayerMagicState state, float dealt) {
        if (dealt <= 0.0F || !ClassPassiveEffects.on(state, MagicPassiveContent.HAEMOPHAGE.id())) {
            return;
        }
        player.heal(Math.min(HAEMOPHAGE_CAP, dealt * HAEMOPHAGE_SHARE));
    }

    /** Vessel Siphon credits the kill, unless a Weeping Vessel has already refused it. */
    static void onKill(PlayerMagicState state, DamageSource source) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.VESSEL_SIPHON.id())
                || !PassiveHooks.isSpellKill(source)) {
            return;
        }
        state.addBloodVessel(VESSEL_SIPHON_BLOOD);
    }

    /** Unfeeling shortens anything the world tries to hold the player with. */
    static float statusScale(PlayerMagicState state) {
        return ClassPassiveEffects.on(state, MagicPassiveContent.UNFEELING.id()) ? UNFEELING_STATUS : 1.0F;
    }

    /** Second Heart, recomputed on the slow tick like every other pool bonus. */
    static int bonusBarrier(PlayerMagicState state) {
        return ClassPassiveEffects.on(state, MagicPassiveContent.SECOND_HEART.id()) ? SECOND_HEART_BARRIER : 0;
    }

    /**
     * Ironblood refuses one killing blow and is spent doing it.
     *
     * <p>Emptying the Vessel as well is what stops it being a free life: the ritual that bought it
     * cost a full Vessel, and the way out of the grave costs the next one too.
     */
    static boolean cheatDeath(ServerPlayer player, PlayerMagicState state) {
        if (!ClassPassiveEffects.on(state, MagicPassiveContent.IRONBLOOD.id())) {
            return false;
        }
        player.setHealth(1.0F);
        state.addBloodVessel(-PlayerMagicState.MAX_BLOOD_VESSEL);
        state.removePassive(MagicPassiveContent.IRONBLOOD.id());
        PassiveHooks.puff(player, ParticleTypes.DAMAGE_INDICATOR, 24, 0.6D);
        return true;
    }

    /**
     * The attribute-driven boons, re-applied every slow tick.
     *
     * <p>Transient modifiers do not survive a relog, and a boon that ends has to take its modifier
     * with it, so this reasserts the whole set rather than reacting to changes.
     */
    static void attributes(ServerPlayer player, PlayerMagicState state) {
        applyOrClear(player, Attributes.ATTACK_DAMAGE, EDGE_MODIFIER, EDGE,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL,
                ClassPassiveEffects.on(state, MagicPassiveContent.CRIMSON_EDGE.id()));

        boolean pulse = ClassPassiveEffects.on(state, MagicPassiveContent.QUICKENED_PULSE.id());
        applyOrClear(player, Attributes.MOVEMENT_SPEED, PULSE_SPEED_MODIFIER, PULSE,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, pulse);
        applyOrClear(player, Attributes.ATTACK_SPEED, PULSE_SWING_MODIFIER, PULSE,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, pulse);

        boolean reach = ClassPassiveEffects.on(state, MagicPassiveContent.LONG_REACH.id());
        applyOrClear(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ENTITY_MODIFIER, REACH_ENTITY,
                AttributeModifier.Operation.ADD_VALUE, reach);
        applyOrClear(player, Attributes.BLOCK_INTERACTION_RANGE, REACH_BLOCK_MODIFIER, REACH_BLOCK,
                AttributeModifier.Operation.ADD_VALUE, reach);

        applyOrClear(player, Attributes.KNOCKBACK_RESISTANCE, UNFEELING_MODIFIER, UNFEELING_KNOCKBACK,
                AttributeModifier.Operation.ADD_VALUE,
                ClassPassiveEffects.on(state, MagicPassiveContent.UNFEELING.id()));

        boolean footing = ClassPassiveEffects.on(state, MagicPassiveContent.SURE_FOOTING.id());
        applyOrClear(player, Attributes.STEP_HEIGHT, FOOTING_STEP_MODIFIER, FOOTING_STEP,
                AttributeModifier.Operation.ADD_VALUE, footing);
        applyOrClear(player, Attributes.SAFE_FALL_DISTANCE, FOOTING_FALL_MODIFIER, FOOTING_SAFE_FALL,
                AttributeModifier.Operation.ADD_VALUE, footing);
    }

    /** Same shape as {@code WildPassives.applyOrClear}, with the operation left to the caller. */
    static void applyOrClear(LivingEntity entity, Holder<Attribute> attribute, ResourceLocation id,
            double amount, AttributeModifier.Operation operation, boolean wanted) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        if (!wanted) {
            instance.removeModifier(id);
            return;
        }
        if (instance.getModifier(id) == null) {
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }
}
