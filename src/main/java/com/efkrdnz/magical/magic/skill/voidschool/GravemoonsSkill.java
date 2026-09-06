package com.efkrdnz.magical.magic.skill.voidschool;

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
import com.efkrdnz.magical.magic.service.SpellIntercept;
import com.efkrdnz.magical.magic.skill.SkillModule;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * VOID T1 (BARRIER) - ORBIT / SELF_SATELLITES / RING_OF_BODIES. Three obsidian shards orbit the
 * caster: each strikes what it touches (three strikes then shatters) and shatters to erase the
 * first hostile projectile that reaches it. Three hits or three blocked shots, any mix.
 * Sneak = a tighter, reversed orbit.
 */
public final class GravemoonsSkill implements SkillModule {
    public static final int SHARDS = 3;
    private static final int REVOLUTION = 40;
    private static final double REACH = 0.9D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.GRAVEMOONS;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity ring = SpellEffectEntity.spawn(ctx, ctx.feet().add(0.0D, 1.2D, 0.0D), Math.max(60, ctx.duration()), (ctx.sneak() ? 1.4F : 2.2F) * Math.max(0.5F, ctx.size()), ctx.look());
                ring.setExtra(0b111); // alive mask
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
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

    /** World position of shard i at the given age (shared with the painter through the same math). */
    public static Vec3 shardOffset(int i, float age, float radius, boolean reversed, float speedScale) {
        double angle = (age / (REVOLUTION / Math.max(0.3F, speedScale)) * Math.PI * 2.0D) * (reversed ? -1.0D : 1.0D) + i * Math.PI * 2.0D / SHARDS;
        return new Vec3(Math.cos(angle) * radius, Math.sin(age * 0.09D + i) * 0.15D, Math.sin(angle) * radius);
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            LivingEntity owner = entity.livingOwner();
            if (owner == null || !owner.isAlive()) {
                entity.finish();
                return;
            }
            entity.setPos(owner.getX(), owner.getY() + 1.2D, owner.getZ());
            int alive = entity.extra() & 0b111;
            if (alive == 0) {
                entity.finish();
                return;
            }
            if (entity.tickCount == 1) {
                syncShards(entity, alive);
            }
            CompoundTag data = entity.serverData();
            for (int i = 0; i < SHARDS; i++) {
                if ((alive & (1 << i)) == 0) {
                    continue;
                }
                Vec3 shard = entity.position().add(shardOffset(i, entity.tickCount, entity.radius(), entity.sneakMode(), entity.speed()));
                boolean shattered = false;
                for (Entity projectile : SpellIntercept.hostileProjectiles(level, shard, REACH, owner)) {
                    SpellIntercept.erase(projectile);
                    shattered = true;
                    break;
                }
                if (!shattered) {
                    for (LivingEntity hostile : SkillTargets.hostilesWithin(level, owner, shard, REACH)) {
                        String key = "icd_" + i + "_" + hostile.getId();
                        if (data.getInt(key) > entity.tickCount) {
                            continue;
                        }
                        data.putInt(key, entity.tickCount + 10);
                        SkillTargets.hurt(level, owner, hostile, entity.damage(), entity.definition(), true);
                        SkillTargets.shove(hostile, entity.position(), 0.6D * Math.max(0.5D, entity.knockback()), 0.1D);
                        int strikes = data.getInt("strikes_" + i) + 1;
                        data.putInt("strikes_" + i, strikes);
                        if (strikes >= 3) {
                            shattered = true;
                        }
                        break;
                    }
                }
                if (shattered) {
                    alive &= ~(1 << i);
                    entity.setExtra(alive);
                    syncShards(entity, alive);
                    SpellFx.impact(level, entity.definition(), shard, shard.subtract(entity.position()).normalize(), null, owner, 1.0F);
                }
            }
        };
    }

    /** The painter reads the alive mask and the orbit direction from the synced tag. */
    private static void syncShards(SpellEffectEntity entity, int alive) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Alive", alive);
        tag.putBoolean("Rev", entity.sneakMode());
        entity.setSyncedData(tag);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.VOID)
                .circle(CircleScript.of(SchoolMaterial.VOID).emblem(EmblemId.COMET).frame(3).band(GlyphKind.FACET_BAND, 9, ColorRole.BASE).band(GlyphKind.TICK_BAND, 36, ColorRole.DIM).stamps(StampId.KITE, 3).orbit(3, 0.84F, 3).core(CoreKind.CROSS).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.custom("gravemoons", 2.2F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.GLASS_SPLINTER, FxKinds.Overlay.CRACKED_GLASS)
                .bounds(3.5F, 2.0F, 2.0F);
    }
}
