package com.efkrdnz.magical.magic.skill.fusion;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.CircleScript;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.CoreKind;
import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.ReleaseMode;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * SPELL CREATOR (glint + rip_current) - HEAL_VOLUME / AIM_SURFACE_FOUNTAIN / HANGING_DROPLET_SPHERE.
 * A fountain of lit water whose droplets hang in a sphere: allies inside are healed (and slowly
 * shielded), enemies inside are dazzled and scalded. The roster's only healing volume. Sneak =
 * fountain at your own feet.
 */
public final class DawnwellSkill implements SkillModule {
    private static final int BARRIER_CAP = 8;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.DAWNWELL;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 pos = ctx.sneak() ? ctx.feet() : ctx.aim().point();
                SpellEffectEntity well = SpellEffectEntity.spawn(ctx, pos, Math.max(40, ctx.duration()), Math.max(2.0F, ctx.size()), new Vec3(0.0D, 1.0D, 0.0D));
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 12.0D;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public boolean aimDropsToGround() {
                return true;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.defence();
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            if (entity.tickCount % 10 != 0) {
                return;
            }
            Vec3 centre = entity.position().add(0.0D, 1.5D, 0.0D);
            double radius = entity.radius();
            CompoundTag data = entity.serverData();
            for (LivingEntity ally : SkillTargets.alliesWithin(level, entity.owner(), centre, radius)) {
                ally.heal(1.0F);
                ally.clearFire();
                if (entity.tickCount % 20 == 0 && ally instanceof ServerPlayer player) {
                    String key = "b_" + player.getUUID();
                    int given = data.getInt(key);
                    if (given < BARRIER_CAP) {
                        data.putInt(key, given + 1);
                        var state = player.getData(MagicalAttachments.MAGIC_STATE);
                        state.addBarrier(1);
                        state.sync(player);
                    }
                }
            }
            for (LivingEntity hostile : SkillTargets.hostilesWithin(level, entity.owner(), centre, radius)) {
                hostile.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 0, false, false));
                MagicStatusService.apply(hostile, MagicStatus.DAZZLED, 12, entity.definition().id(), entity.owner());
                SkillTargets.hurt(level, entity.owner(), hostile, entity.damage(), entity.definition().id());
            }
            SpellFx.zoneTick(level, entity.definition(), entity.position(), (float) radius * 0.5F);
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.LIGHT)
                .palette(3)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.WELL).frame(3).band(GlyphKind.PETAL_BAND, 16, ColorRole.BASE).band(GlyphKind.WAVE_BAND, 8, ColorRole.HOT).stamps(StampId.DROP, 8).orbit(3, 0.84F, 4).core(CoreKind.RIPPLE).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.swarm(Silhouette.Form.SPHERE, FxKinds.Smoke.DROPLET, 60, 4.0F).withRole(ColorRole.BRIGHT))
                .silhouette(Silhouette.filament(Silhouette.Form.COLUMN, FxKinds.Filament.LIQUID_ROPE, 3, 0.25F, 3.0F, 1))
                .silhouette(Silhouette.mark(FxKinds.Mark.RIPPLES, 4.0F, 8).withOpacity(0.6F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.LENS_SPARKLE, FxKinds.Overlay.WATER_DROPLETS)
                .budget(2)
                .bounds(6.0F, 6.0F, 1.0F);
    }
}
