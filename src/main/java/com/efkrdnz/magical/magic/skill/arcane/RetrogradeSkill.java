package com.efkrdnz.magical.magic.skill.arcane;

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
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * ARCANE T1 - REWIND_POSITION / AIM_SURFACE_SNAPSHOT / RECORD_RADIUS. Inscribe a glyph; after a
 * short windup it snapshots every hostile within 6 blocks, and exactly 60 ticks later teleports
 * each one back to where it stood, hurting it by how far it had travelled. Sneak = at your feet.
 */
public final class RetrogradeSkill implements SkillModule {
    private static final int WINDUP = 5;
    private static final int WINDOW = 60;
    private static final int LINGER = 14;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.RETROGRADE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 pos = ctx.sneak() ? ctx.feet().add(0.0D, 0.05D, 0.0D) : ctx.aim().point();
                Vec3 normal = ctx.sneak() ? new Vec3(0.0D, 1.0D, 0.0D) : ctx.aim().normal();
                SpellEffectEntity glyph = SpellEffectEntity.spawn(ctx, pos, WINDUP + Math.max(20, ctx.duration()) + LINGER, 6.0F * Math.max(0.5F, ctx.size()), normal);
                glyph.setExtra(Math.max(20, ctx.duration()));
                return CastResult.SUCCESS;
            }

            @Override
            public boolean aimDropsToGround() {
                return false;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(2.0F, 14.0F);
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
            int window = Math.max(20, entity.extra());
            if (entity.tickCount == WINDUP) {
                ListTag records = new ListTag();
                int n = 0;
                for (LivingEntity hostile : SkillTargets.hostilesWithin(level, entity.owner(), entity.position(), entity.radius())) {
                    if (n++ >= 6) {
                        break;
                    }
                    CompoundTag rec = new CompoundTag();
                    rec.putUUID("Id", hostile.getUUID());
                    rec.putDouble("X", hostile.getX());
                    rec.putDouble("Y", hostile.getY());
                    rec.putDouble("Z", hostile.getZ());
                    rec.putFloat("Yaw", hostile.getYRot());
                    rec.putFloat("Pitch", hostile.getXRot());
                    records.add(rec);
                    SpellFx.followingCircle(level, entity.definition(), com.efkrdnz.magical.entity.MagicCircleEffectEntity.ROLE_TARGET, hostile, 0.7F, window);
                }
                entity.serverData().put("Records", records);
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                SpellFx.zoneTick(level, entity.definition(), entity.position(), entity.radius() / 3.0F);
            } else if (entity.tickCount == WINDUP + window) {
                ListTag records = entity.serverData().getList("Records", Tag.TAG_COMPOUND);
                for (int i = 0; i < records.size(); i++) {
                    CompoundTag rec = records.getCompound(i);
                    Entity target = level.getEntity(rec.getUUID("Id"));
                    if (!(target instanceof LivingEntity living) || !living.isAlive()) {
                        continue;
                    }
                    Vec3 origin = new Vec3(rec.getDouble("X"), rec.getDouble("Y"), rec.getDouble("Z"));
                    if (living.position().distanceToSqr(origin) > 40.0D * 40.0D) {
                        continue;
                    }
                    double travelled = living.position().distanceTo(origin);
                    living.stopRiding();
                    living.teleportTo(origin.x, origin.y, origin.z);
                    living.setYRot(rec.getFloat("Yaw"));
                    living.setXRot(rec.getFloat("Pitch"));
                    living.setDeltaMovement(Vec3.ZERO);
                    living.hurtMarked = true;
                    float damage = Math.min(14.0F, entity.damage() + (float) travelled * 0.35F);
                    SkillTargets.hurt(level, entity.owner(), living, damage, entity.definition(), true);
                }
                entity.setPhase(SpellEffectEntity.PHASE_CLOSING);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.SPIRAL).frame(7).band(GlyphKind.RUNE_BAND, 60).stamps(StampId.SPIRAL, 6).core(CoreKind.RIPPLE).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.glyph(1.6F))
                .silhouette(Silhouette.mark(FxKinds.Mark.RIPPLES, 6.0F, 3).withOpacity(0.6F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.CLOCK_SPOKES, FxKinds.Smoke.RUNE_MOTE, FxKinds.Overlay.TUNNEL)
                .bounds(7.0F, 3.0F, 1.0F);
    }
}
