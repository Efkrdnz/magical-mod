package com.efkrdnz.magical.forge.art;

import com.efkrdnz.magical.entity.forge.ForgeEffectStyle;
import com.efkrdnz.magical.entity.forge.ForgeZoneKind;
import com.efkrdnz.magical.forge.ElementDefinition;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.StrikeContext;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/** RADIANT's four Arts: the dawn cut, the sanctified ring, the piercing crescent and the ascent. */
public final class RadiantArts {

    private static final float DAWN_UNDEAD_BONUS = 0.5f;
    private static final int DAWN_GLOW_TICKS = 100;

    private static final float RING_RADIUS = 3.0f;
    private static final int RING_LIFE = 40;

    private static final double CRESCENT_LENGTH = 6.0;
    private static final double CRESCENT_HALF_WIDTH = 1.2;
    private static final int CRESCENT_EXTRA_TARGETS = 2;
    private static final int CRESCENT_GLOW_TICKS = 100;

    private static final double ASCENT_LIFT = 0.8;
    private static final float ASCENT_SELF_HEAL = 2.0f;
    private static final double ASCENT_ALLY_RADIUS = 3.0;
    private static final float ASCENT_ALLY_HEAL = 1.0f;

    private RadiantArts() {}

    /** Dawn Edge - the finishing slash burns half again as deep into the undead and lights the body up. */
    public static void dawnEdge(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.DAWN_EDGE, context)) {
            return;
        }
        if (target.getType().is(EntityTypeTags.UNDEAD)) {
            ArtSupport.hurt(owner, target, context.dealtDamage() * DAWN_UNDEAD_BONUS);
        }
        ArtSupport.apply(owner, target, new MobEffectInstance(MobEffects.GLOWING, DAWN_GLOW_TICKS, 0));
        ArtSupport.burst(level, ArtSupport.centre(target), ForgeEffectStyle.RADIANT_CROSS, element,
                target.getBbWidth());
    }

    /** Sanctified Ring - the finishing spin leaves consecrated ground: it mends allies and burns the undead. */
    public static void sanctifiedRing(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.SANCTIFIED_RING, context) || !context.firstBody()) {
            return;
        }
        ArtSupport.zone(level, owner, owner.position(), ForgeZoneKind.SANCTIFIED_RING, element, RING_RADIUS,
                RING_LIFE, 0.0f);
    }

    /** Light Crescent - the crescent runs on through two more bodies, marking every one of them. */
    public static void lightCrescent(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.LIGHT_CRESCENT, context)) {
            return;
        }
        ArtSupport.apply(owner, target, new MobEffectInstance(MobEffects.GLOWING, CRESCENT_GLOW_TICKS, 0));
        if (!context.firstBody()) {
            return;
        }
        for (LivingEntity pierced : ArtSupport.alongLine(level, ArtSupport.centre(target),
                ArtSupport.direction(context), CRESCENT_LENGTH, CRESCENT_HALF_WIDTH, owner, target,
                CRESCENT_EXTRA_TARGETS)) {
            ArtSupport.hurt(owner, pierced, context.dealtDamage());
            ArtSupport.apply(owner, pierced, new MobEffectInstance(MobEffects.GLOWING, CRESCENT_GLOW_TICKS, 0));
            ArtSupport.burst(level, ArtSupport.centre(pierced), ForgeEffectStyle.RADIANT_CROSS, element,
                    pierced.getBbWidth());
        }
    }

    /** Ascension - the heavy rising cut lifts the target and the light mends whoever stands nearby. */
    public static void ascension(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.ASCENSION, context)) {
            return;
        }
        ArtSupport.push(owner, target, new Vec3(0.0, ASCENT_LIFT, 0.0));
        if (!context.firstBody()) {
            return;
        }
        owner.heal(ASCENT_SELF_HEAL);
        for (LivingEntity ally : ArtSupport.around(level, owner.position(), ASCENT_ALLY_RADIUS, owner, target)) {
            if (ally instanceof Player) {
                ally.heal(ASCENT_ALLY_HEAL);
            }
        }
        ArtSupport.burst(level, owner.position(), ForgeEffectStyle.RADIANT_CROSS, element, (float) ASCENT_ALLY_RADIUS);
    }
}
