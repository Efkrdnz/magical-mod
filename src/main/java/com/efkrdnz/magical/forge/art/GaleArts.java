package com.efkrdnz.magical.forge.art;

import com.efkrdnz.magical.entity.forge.ForgeEffectStyle;
import com.efkrdnz.magical.entity.forge.ForgeZoneKind;
import com.efkrdnz.magical.forge.ElementDefinition;
import com.efkrdnz.magical.forge.ForgedWeapon;
import com.efkrdnz.magical.forge.StrikeContext;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** GALE's four Arts: the flying blade, the cyclone, the updraft and the flurry's step. */
public final class GaleArts {

    private static final double CUTTER_LENGTH = 10.0;
    private static final double CUTTER_HALF_WIDTH = 1.2;
    private static final int CUTTER_TARGETS = 3;
    private static final int CUTTER_TRAIL_MARKS = 3;

    private static final float CYCLONE_RADIUS = 4.0f;
    private static final int CYCLONE_LIFE = 30;

    private static final double UPDRAFT_LIFT = 0.6;
    private static final int UPDRAFT_SUSPEND_TICKS = 20;
    private static final int UPDRAFT_SLOW_FALL_TICKS = 60;

    private static final double STEP_DISTANCE = 0.5;

    private GaleArts() {}

    /**
     * Wind Cutter - the slash keeps going as a blade of wind for ten blocks, so the swing that
     * connected in melee range also reaches whatever was standing behind it.
     */
    public static void windCutter(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.WIND_CUTTER, context) || !context.firstBody()) {
            return;
        }
        Vec3 forward = ArtSupport.direction(context);
        Vec3 from = owner.getEyePosition();
        for (LivingEntity cut : ArtSupport.alongLine(level, from, forward, CUTTER_LENGTH, CUTTER_HALF_WIDTH,
                owner, target, CUTTER_TARGETS)) {
            ArtSupport.hurt(owner, cut, context.dealtDamage());
            ArtSupport.burst(level, ArtSupport.centre(cut), ForgeEffectStyle.GALE_SWIRL, element, cut.getBbWidth());
        }
        for (int mark = 1; mark <= CUTTER_TRAIL_MARKS; mark++) {
            double along = CUTTER_LENGTH * mark / (double) CUTTER_TRAIL_MARKS;
            ArtSupport.burst(level, from.add(forward.scale(along)), ForgeEffectStyle.GALE_SWIRL, element, 1.0f);
        }
    }

    /** Cyclone - the finishing spin winds up, hauls everything in, then throws the lot back out. */
    public static void cyclone(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.CYCLONE, context) || !context.firstBody()) {
            return;
        }
        ArtSupport.zone(level, owner, owner.position(), ForgeZoneKind.CYCLONE, element, CYCLONE_RADIUS, CYCLONE_LIFE,
                0.0f);
    }

    /** Updraft - the heavy rising cut hangs the target on the wind, then lets it drift down. */
    public static void updraft(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.UPDRAFT, context)) {
            return;
        }
        ArtSupport.push(owner, target, new Vec3(0.0, UPDRAFT_LIFT, 0.0));
        ArtSupport.apply(owner, target, new MobEffectInstance(MobEffects.LEVITATION, UPDRAFT_SUSPEND_TICKS, 0));
        ArtSupport.apply(owner, target, new MobEffectInstance(MobEffects.SLOW_FALLING, UPDRAFT_SLOW_FALL_TICKS, 0));
        ArtSupport.burst(level, ArtSupport.centre(target), ForgeEffectStyle.GALE_SWIRL, element,
                Math.max(1.0f, target.getBbHeight()));
    }

    /**
     * Gale Step - every pulse of the flurry carries the wielder half a block further onto the
     * target.
     *
     * <p>Once per <em>pulse</em>, not once per body: the Art runs against everything the pulse
     * touched, and three pulses into three mobs would otherwise dash four and a half blocks off one
     * press. The pulse's extra reach is not applied here - it is folded into the strike's own reach
     * when the press resolves ({@link com.efkrdnz.magical.forge.strike.ForgeStrikeMath#artReachBonus}),
     * because by the time a strike touches a body its reach has already decided what it could
     * touch.</p>
     */
    public static void galeStep(ServerLevel level, ServerPlayer owner, LivingEntity target, ForgedWeapon weapon,
            StrikeContext context) {
        ElementDefinition element = ArtSupport.element(weapon);
        if (element == null || !ArtSupport.triggered(ForgeArt.GALE_STEP, context) || !context.firstBodyOfPass()) {
            return;
        }
        Vec3 forward = ArtSupport.direction(context);
        ArtSupport.push(owner, owner, new Vec3(forward.x * STEP_DISTANCE, 0.0, forward.z * STEP_DISTANCE));
        ArtSupport.burst(level, owner.position(), ForgeEffectStyle.GALE_SWIRL, element, 1.0f);
    }
}
