package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
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
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * WARSMITH - HANG_THEN_DROP / AIMED_POINT / GROUND_SLAM. A forge anvil the size of a cart hangs in
 * the air over the aim point for a beat, then falls. Whatever the slam hurts is paid back to the
 * smith as barrier, so the Warsmith armours themselves by hitting things - which is the only apex on
 * the Blacksmith tree that grants an active skill at all.
 */
public final class AnvilFallSkill implements SkillModule {
    private static final int HANG = 14;
    private static final double DROP_HEIGHT = 7.0D;
    /** Share of the damage dealt that comes back to the caster as barrier. */
    private static final float BARRIER_SHARE = 0.35F;
    private static final int BARRIER_CAP = 24;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.ANVIL_FALL;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 ground = ctx.aim().point();
                Vec3 top = ground.add(0.0D, DROP_HEIGHT, 0.0D);
                SpellEffectEntity anvil = SpellEffectEntity.spawn(ctx, top, HANG + 12, ctx.size(), new Vec3(0.0D, -1.0D, 0.0D));
                CompoundTag data = anvil.serverData();
                data.putDouble("GX", ground.x);
                data.putDouble("GY", ground.y);
                data.putDouble("GZ", ground.z);
                SpellFx.release(ctx.caster(), ctx.definition(), ctx.look());
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 22.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(5.0F, 20.0F);
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
                CompoundTag data = entity.serverData();
                Vec3 ground = new Vec3(data.getDouble("GX"), data.getDouble("GY"), data.getDouble("GZ"));
                if (entity.tickCount <= HANG) {
                    // Hanging: a slight rise so the drop reads as a release rather than a spawn.
                    entity.setPos(ground.add(0.0D, DROP_HEIGHT + entity.tickCount * 0.02D, 0.0D));
                    entity.setValue(entity.tickCount / (float) HANG);
                    return;
                }
                float fall = Math.min(1.0F, (entity.tickCount - HANG) / 10.0F);
                entity.setPos(ground.add(0.0D, DROP_HEIGHT * (1.0F - fall * fall), 0.0D));
                entity.setValue(1.0F);
                if (fall >= 1.0F) {
                    entity.finish();
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                CompoundTag data = entity.serverData();
                Vec3 ground = new Vec3(data.getDouble("GX"), data.getDouble("GY"), data.getDouble("GZ"));
                float dealt = 0.0F;
                for (LivingEntity victim : SkillTargets.hostilesWithin(level, entity.owner(), ground, entity.radius() * 1.6D)) {
                    SkillTargets.hurt(level, entity.owner(), victim, entity.damage(), entity.definition(), true);
                    SkillTargets.shove(victim, ground, 0.9D * Math.max(0.3D, entity.knockback()), 0.45D);
                    dealt += entity.damage();
                }
                // The forge takes its cut back: every blow the anvil lands plates the smith.
                if (dealt > 0.0F && entity.owner() instanceof ServerPlayer smith) {
                    PlayerMagicState state = smith.getData(MagicalAttachments.MAGIC_STATE);
                    state.addBarrier(Math.min(BARRIER_CAP, Math.round(dealt * BARRIER_SHARE)));
                    state.sync(smith);
                }
                SpellFx.impact(level, entity.definition(), ground, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 2.0F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .palette(2)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.ANCHOR).frame(13).band(GlyphKind.BRAID_BAND, 24).band(GlyphKind.SOLID_RING, 6).stamps(StampId.BAR, 8).core(CoreKind.EMBER_PIT).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.body(Silhouette.Form.SLAB, FxKinds.Body.METAL_BANDS, 3, 1.1F, 0.7F))
                .silhouette(Silhouette.swarm(Silhouette.Form.CLOUD, FxKinds.Smoke.SPARK_STREAK, 10, 0.9F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.EMBER_CLUSTER, FxKinds.Overlay.SHOCK_RING)
                .bounds(3.0F, 8.0F, 1.4F);
    }
}
