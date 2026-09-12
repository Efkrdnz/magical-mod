package com.efkrdnz.magical.forge.art;

import java.util.List;

import com.efkrdnz.magical.entity.forge.ForgeEffectStyle;
import com.efkrdnz.magical.forge.ElementDefinition;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.StrikeContext;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The Arts that belong to a weapon rather than to an element.
 *
 * <p>Every other file in this package is named for the element whose four Arts it holds. These
 * seven are keyed on the archetype and the temper instead - what makes THOUSAND_CUTS that move is
 * the dagger and the rush it was tempered for, not what the blade is made of - so they live
 * together here rather than being scattered across element files none of them belongs to.
 */
public final class WeaponArts {

    /** Thousand Cuts: how many extra cuts the dagger makes, and what share of the blow each is. */
    private static final int CUTS = 6;
    private static final int CUTS_PER_GRADE = 1;
    private static final float CUT_SHARE = 0.18f;
    private static final double CUT_RING = 1.1;

    /** Crimson Tithe: the share of the target's maximum health taken, and the barrier it buys. */
    private static final float TITHE_MAX_HEALTH = 0.04f;
    private static final float TITHE_MAX_HEALTH_PER_GRADE = 0.01f;
    private static final float TITHE_TO_BARRIER = 1.5f;

    /** Heartseeker: how far through a target has to be before its armour stops counting. */
    private static final float SEEK_THRESHOLD = 0.5f;
    private static final float SEEK_SHARE = 0.35f;

    /** Red Harvest: barrier refunded for every body the swing finished. */
    private static final int HARVEST_REFUND = 6;
    private static final int HARVEST_REFUND_PER_GRADE = 2;
    private static final float HARVEST_LETHAL = 0.0f;

    /** Breach: the shockwave's reach, its share of the blow, and how long the guard stays stripped. */
    private static final double BREACH_RADIUS = 3.5;
    private static final float BREACH_SHARE = 0.45f;
    private static final int BREACH_TICKS = 60;
    private static final double BREACH_PUSH = 0.6;

    /** Impale: how long the point holds, and how hard it holds. */
    private static final int IMPALE_TICKS = 40;
    private static final int IMPALE_SLOW_LEVEL = 5;
    private static final int IMPALE_JUMP_LEVEL = 128;

    /** Ribbons: the bleed each hook lays on, and the ceiling it stacks to. */
    private static final int RIBBON_TICKS = 80;
    private static final int RIBBON_MAX_LEVEL = 3;

    private WeaponArts() {}

    /**
     * Thousand Cuts - a rush-tempered dagger stops making one cut and makes a ring of them.
     *
     * <p>Gated on {@link StrikeContext#firstBodyOfPass()} rather than on the press, so a flurry of
     * dagger slashes rings out once per pulse instead of once for the whole combo - but a slash
     * that threads three bodies still only rings once, which is what keeps it from multiplying
     * itself against a crowd.
     */
    public static void thousandCuts(ServerLevel level, ServerPlayer owner, LivingEntity target,
            ForgedWeapon weapon, StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.THOUSAND_CUTS, context)
                || !context.firstBodyOfPass()) {
            return;
        }
        int cuts = CUTS + CUTS_PER_GRADE * ArtSupport.grade(weapon);
        float each = context.dealtDamage() * CUT_SHARE;
        Vec3 centre = ArtSupport.centre(target);
        // Every body inside the ring takes every cut: the move is the dagger going round the
        // target, and anything else standing in that ring is standing in the same storm.
        List<LivingEntity> caught = ArtSupport.around(level, centre, CUT_RING, owner, null);
        for (int cut = 0; cut < cuts; cut++) {
            double angle = cut * (Math.PI * 2.0) / cuts;
            Vec3 at = centre.add(Math.cos(angle) * CUT_RING, 0.0, Math.sin(angle) * CUT_RING);
            ArtSupport.burst(level, at, ForgeEffectStyle.VOID_IMPLOSION, element, 0.5f);
            for (LivingEntity body : caught) {
                ArtSupport.hurt(owner, body, each);
            }
        }
    }

    /**
     * Crimson Tithe - a charged blood thrust takes a share of what the target <em>is</em> rather
     * than a share of the blow, and gives it to the wielder as barrier.
     *
     * <p>Reading maximum health rather than dealt damage is the point: it is the one Art in the
     * game that hurts a thing with a large pool more than a thing with a large armour rating, which
     * is what makes it the answer to something the rest of the catalogue cannot dent.
     */
    public static void crimsonTithe(ServerLevel level, ServerPlayer owner, LivingEntity target,
            ForgedWeapon weapon, StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.CRIMSON_TITHE, context)
                || !context.firstBody()) {
            return;
        }
        float share = TITHE_MAX_HEALTH + TITHE_MAX_HEALTH_PER_GRADE * ArtSupport.grade(weapon);
        float taken = target.getMaxHealth() * share;
        if (taken <= 0.0f) {
            return;
        }
        ArtSupport.hurt(owner, target, taken);
        PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
        state.addBarrier(Math.round(taken * TITHE_TO_BARRIER));
        state.sync(owner);
        ArtSupport.burst(level, ArtSupport.centre(target), ForgeEffectStyle.VENOM_DRIP, element, 1.2f);
    }

    /**
     * Heartseeker - a blood dagger lunging at something already half gone puts a share of the blow
     * straight through, past whatever it was wearing.
     *
     * <p>The extra lands as magic damage, which is how everything in the forge bypasses armour, so
     * this needs no new damage path - only the condition that makes it worth building a dagger for.
     */
    public static void heartseeker(ServerLevel level, ServerPlayer owner, LivingEntity target,
            ForgedWeapon weapon, StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.HEARTSEEKER, context)) {
            return;
        }
        float max = target.getMaxHealth();
        if (max <= 0.0f || target.getHealth() / max > SEEK_THRESHOLD) {
            return;
        }
        ArtSupport.hurt(owner, target, context.dealtDamage() * SEEK_SHARE);
        ArtSupport.burst(level, ArtSupport.centre(target), ForgeEffectStyle.VENOM_DRIP, element, 0.8f);
    }

    /**
     * Red Harvest - a finishing reap pays the reaper back for everything that died inside it.
     *
     * <p>Checked against the target's health after the blow rather than by listening for a death
     * event: the strike pipeline has no death hook, and a body at or below zero here is one this
     * swing finished. A body something else killed is never in this loop to be counted.
     */
    public static void redHarvest(ServerLevel level, ServerPlayer owner, LivingEntity target,
            ForgedWeapon weapon, StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.RED_HARVEST, context)
                || target.getHealth() > HARVEST_LETHAL) {
            return;
        }
        PlayerMagicState state = owner.getData(MagicalAttachments.MAGIC_STATE);
        state.addBarrier(HARVEST_REFUND + HARVEST_REFUND_PER_GRADE * ArtSupport.grade(weapon));
        state.sync(owner);
        ArtSupport.burst(level, ArtSupport.centre(target), ForgeEffectStyle.VENOM_DRIP, element, 1.5f);
    }

    /**
     * Breach - a heft-tempered greatsword coming down opens a shockwave that strips guard.
     *
     * <p>"Strips guard" is Weakness: there is no guard stat on a mob to take away, and Weakness is
     * what the rest of the mod already means by a body that cannot bring its weight to bear.
     */
    public static void breach(ServerLevel level, ServerPlayer owner, LivingEntity target,
            ForgedWeapon weapon, StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.BREACH, context) || !context.firstBody()) {
            return;
        }
        Vec3 centre = ArtSupport.centre(target);
        float wave = context.dealtDamage() * BREACH_SHARE;
        for (LivingEntity caught : ArtSupport.around(level, centre, BREACH_RADIUS, owner, null)) {
            ArtSupport.hurt(owner, caught, wave);
            ArtSupport.apply(owner, caught, new MobEffectInstance(MobEffects.WEAKNESS, BREACH_TICKS, 1));
            ArtSupport.pushFrom(owner, caught, centre, BREACH_PUSH);
        }
        ArtSupport.burst(level, centre, ForgeEffectStyle.TERRA_SHARDS, element, (float) BREACH_RADIUS);
    }

    /**
     * Impale - a coiled spear driven home pins whatever it found.
     *
     * <p>Slowness deep enough to stop movement outright, rather than a bespoke pin: the effect is
     * already synced, already shown on the target's status bar, and already cleared by milk and by
     * death, none of which a hand-rolled pin would be. The negative jump boost stops it hopping out.
     */
    public static void impale(ServerLevel level, ServerPlayer owner, LivingEntity target,
            ForgedWeapon weapon, StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.IMPALE, context)) {
            return;
        }
        ArtSupport.apply(owner, target,
                new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, IMPALE_TICKS, IMPALE_SLOW_LEVEL));
        ArtSupport.apply(owner, target,
                new MobEffectInstance(MobEffects.JUMP, IMPALE_TICKS, IMPALE_JUMP_LEVEL));
        ArtSupport.burst(level, ArtSupport.centre(target), ForgeEffectStyle.STORM_FORK, element, 0.9f);
    }

    /**
     * Ribbons - split claws hooking the same body over and over, each pass cutting deeper.
     *
     * <p>The bleed is Wither, stacked by amplifier up to a ceiling. Wither rather than Poison
     * because poison cannot kill, and a bleed that stops one hit short of finishing something is
     * not a bleed.
     */
    public static void ribbons(ServerLevel level, ServerPlayer owner, LivingEntity target,
            ForgedWeapon weapon, StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.RIBBONS, context)) {
            return;
        }
        MobEffectInstance existing = target.getEffect(MobEffects.WITHER);
        int depth = existing == null ? 0 : Math.min(RIBBON_MAX_LEVEL, existing.getAmplifier() + 1);
        ArtSupport.apply(owner, target, new MobEffectInstance(MobEffects.WITHER, RIBBON_TICKS, depth));
        ArtSupport.burst(level, ArtSupport.centre(target), ForgeEffectStyle.VENOM_DRIP, element, 0.6f);
    }
}
