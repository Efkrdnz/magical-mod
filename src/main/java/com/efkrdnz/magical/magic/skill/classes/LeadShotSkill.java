package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.AimResolver;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.CircleScript;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * SHARPSHOOTER - PREDICT_SHOT / DELAYED_HITSCAN_AT_PREDICTION / POINT_BURST. The trigger is pulled
 * now but the round arrives at the LEAD point: where the nearest runner on the sightline will be
 * after the lead ticks, read from its current motion. A glass collet slides ahead of it holding the
 * frozen round, then it materialises and bursts. Sneak = a longer lead.
 */
public final class LeadShotSkill implements SkillModule {
    private static final int LEAD = 10;
    private static final int LONG_LEAD = 20;
    private static final double BURST = 1.5D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.LEAD_SHOT;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                int lead = ctx.sneak() ? LONG_LEAD : LEAD;
                AimResolver.Result aim = ctx.aim();
                LivingEntity runner = aim.living();
                Vec3 start;
                Vec3 end;
                if (runner != null && SkillTargets.isHostile(ctx.caster(), runner)) {
                    Vec3 velocity = runner.position().subtract(new Vec3(runner.xOld, runner.yOld, runner.zOld));
                    Vec3 flat = new Vec3(velocity.x, 0.0D, velocity.z);
                    start = runner.getBoundingBox().getCenter();
                    Vec3 predicted = runner.position().add(flat.scale(lead));
                    Vec3 ground = AimResolver.groundBelow(ctx.level(), predicted.add(0.0D, 1.0D, 0.0D), 6);
                    if (ground != null && Math.abs(ground.y - predicted.y) <= 4.0D) {
                        predicted = ground;
                    }
                    end = predicted.add(0.0D, runner.getBbHeight() * 0.5D, 0.0D);
                } else {
                    start = ctx.eye().add(ctx.look().scale(1.5D));
                    end = aim.point();
                }
                SpellEffectEntity collet = SpellEffectEntity.spawn(ctx, start, lead, (float) BURST * Math.max(0.5F, ctx.size() / 1.5F), end.subtract(start).lengthSqr() > 1.0E-4D ? end.subtract(start).normalize() : ctx.look());
                CompoundTag data = collet.serverData();
                data.putDouble("SX", start.x);
                data.putDouble("SY", start.y);
                data.putDouble("SZ", start.z);
                data.putDouble("EX", end.x);
                data.putDouble("EY", end.y);
                data.putDouble("EZ", end.z);
                SpellFx.release(ctx.caster(), ctx.definition(), ctx.look());
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 40.0D;
            }

            @Override
            public double aimTolerance() {
                return 2.5D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(6.0F, 40.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity entity) {
                CompoundTag d = entity.serverData();
                Vec3 start = new Vec3(d.getDouble("SX"), d.getDouble("SY"), d.getDouble("SZ"));
                Vec3 end = new Vec3(d.getDouble("EX"), d.getDouble("EY"), d.getDouble("EZ"));
                float f = Math.min(1.0F, entity.tickCount / (float) Math.max(1, entity.life()));
                entity.setPos(start.lerp(end, f));
                entity.setValue(f);
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                CompoundTag d = entity.serverData();
                Vec3 end = new Vec3(d.getDouble("EX"), d.getDouble("EY"), d.getDouble("EZ"));
                for (LivingEntity victim : SkillTargets.hostilesWithin(level, entity.owner(), end, entity.radius())) {
                    SkillTargets.hurt(level, entity.owner(), victim, entity.damage(), entity.definition(), true);
                    SkillTargets.shove(victim, end, 0.4D * Math.max(0.3D, entity.knockback()), 0.15D);
                }
                SpellFx.impact(level, entity.definition(), end, entity.direction().scale(-1.0D), null, entity.owner(), 1.2F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SPATIAL)
                .palette(1)
                .circle(CircleScript.of(SchoolMaterial.SPATIAL).emblem(EmblemId.RETICLE).frame(6).band(GlyphKind.TICK_BAND, 40).band(GlyphKind.DASHED_RING, 8).stamps(StampId.DOT, 4).core(CoreKind.CROSS).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.body(Silhouette.Form.SPIKE_CLUSTER, FxKinds.Body.GLASS, 4, 0.35F))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.SHARD_DIAMOND, 0.12F, 4, 2))
                .silhouette(Silhouette.glyph(0.4F).withOffset(-0.1F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.GLASS_SPLINTER, FxKinds.Overlay.FLASH)
                .bounds(2.0F, 1.5F, 1.5F);
    }
}
