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
import com.efkrdnz.magical.magic.service.SafeSpotSearch;
import com.efkrdnz.magical.magic.service.SkillTargets;
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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * SPELL CREATOR (gravemoons + rigid_frame) - ORBIT_ENEMIES / SELF_RADIUS / ENEMY_CAROUSEL. After a
 * telegraph every hostile nearby is forced onto a circular orbit around the caster (who may walk;
 * the ring follows). A dark node sits at one bearing: each pass bites and strips effects. Breaking
 * line of sight for a while frees a captive. Sneak = reverse the orbit.
 */
public final class BlackOrrerySkill implements SkillModule {
    private static final int TELEGRAPH = 10;
    private static final double CATCH = 6.0D;
    private static final double ORBIT = 4.0D;
    private static final int REVOLUTION = 60;
    private static final int LOS_FREE = 20;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.BLACK_ORRERY;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity orrery = SpellEffectEntity.spawn(ctx, ctx.feet().add(0.0D, 1.2D, 0.0D), TELEGRAPH + Math.max(40, ctx.duration()), (float) ORBIT * Math.max(0.6F, ctx.size() / 4.0F), ctx.look());
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(0.0F, 6.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    private static void sync(SpellEffectEntity entity, List<LivingEntity> captives) {
        CompoundTag tag = new CompoundTag();
        int[] ids = new int[captives.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = captives.get(i).getId();
        }
        tag.putIntArray("V", ids);
        tag.putBoolean("Rev", entity.sneakMode());
        entity.setSyncedData(tag);
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
            Vec3 hub = owner.position().add(0.0D, 1.2D, 0.0D);
            entity.setPos(hub);
            CompoundTag data = entity.serverData();
            int t = entity.tickCount;
            if (t < TELEGRAPH) {
                if (t % 2 == 0) {
                    sync(entity, SkillTargets.hostilesWithin(level, owner, hub, CATCH));
                }
                return;
            }
            if (t == TELEGRAPH) {
                ListTag captives = new ListTag();
                List<LivingEntity> caught = SkillTargets.hostilesWithin(level, owner, hub, CATCH);
                for (int i = 0; i < caught.size(); i++) {
                    LivingEntity c = caught.get(i);
                    Vec3 rel = c.position().subtract(owner.position());
                    CompoundTag tag = new CompoundTag();
                    tag.putUUID("Id", c.getUUID());
                    tag.putDouble("Angle", Math.atan2(rel.z, rel.x));
                    tag.putInt("Blind", 0);
                    tag.putBoolean("PastNode", false);
                    captives.add(tag);
                }
                data.put("Captives", captives);
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                sync(entity, caught);
                if (caught.isEmpty()) {
                    entity.finish();
                }
                SpellFx.impact(level, entity.definition(), hub, new Vec3(0.0D, 1.0D, 0.0D), null, owner, 1.2F);
                return;
            }
            double step = Math.PI * 2.0D / REVOLUTION * (entity.sneakMode() ? -1.0D : 1.0D);
            double radius = entity.radius();
            ListTag captives = data.getList("Captives", Tag.TAG_COMPOUND);
            List<LivingEntity> still = new ArrayList<>();
            boolean changed = false;
            for (int i = 0; i < captives.size(); i++) {
                CompoundTag tag = captives.getCompound(i);
                Entity e = level.getEntity(tag.getUUID("Id"));
                if (!(e instanceof LivingEntity captive) || !captive.isAlive()) {
                    captives.remove(i--);
                    changed = true;
                    continue;
                }
                int blind = owner.hasLineOfSight(captive) ? 0 : tag.getInt("Blind") + 1;
                tag.putInt("Blind", blind);
                if (blind >= LOS_FREE) {
                    captives.remove(i--);
                    changed = true;
                    continue;
                }
                double before = tag.getDouble("Angle");
                double angle = before + step;
                tag.putDouble("Angle", angle);
                Vec3 wanted = owner.position().add(Math.cos(angle) * radius, 0.0D, Math.sin(angle) * radius);
                Vec3 clear = SafeSpotSearch.liftClear(level, wanted, captive.getBbWidth(), captive.getBbHeight(), 2.0D);
                Vec3 at = clear != null ? clear : wanted;
                captive.setPos(at.x, at.y, at.z);
                captive.setDeltaMovement(Vec3.ZERO);
                captive.fallDistance = 0.0F;
                captive.hurtMarked = true;
                if (captive instanceof Mob mob) {
                    mob.getNavigation().stop();
                }
                // the node sits at bearing 0: crossing it bites and strips effects
                boolean beyondNode = Math.floorMod(Math.round(Math.toDegrees(angle)), 360L) < 12L;
                if (beyondNode && !tag.getBoolean("PastNode")) {
                    tag.putBoolean("PastNode", true);
                    captive.removeAllEffects();
                    SkillTargets.hurt(level, owner, captive, entity.damage(), entity.definition(), true);
                } else if (!beyondNode) {
                    tag.putBoolean("PastNode", false);
                }
                still.add(captive);
            }
            if (changed) {
                data.put("Captives", captives);
            }
            if (changed || t % 10 == 0) {
                sync(entity, still);
            }
            if (still.isEmpty()) {
                entity.finish();
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.VOID)
                .palette(3)
                .circle(CircleScript.of(SchoolMaterial.VOID).emblem(EmblemId.ORRERY).frame(13).band(GlyphKind.DASHED_RING, 24).band(GlyphKind.TICK_BAND, 48, ColorRole.DIM).band(GlyphKind.FACET_BAND, 8).stamps(StampId.RING, 8).orbit(5, 0.84F, 3).core(CoreKind.VOID_PIT).stack(2, 0.5F).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.custom("black_orrery", 4.0F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.VORTEX_SPIRAL, FxKinds.Smoke.INK_BLOOM, FxKinds.Overlay.TUNNEL)
                .budget(2)
                .bounds(7.0F, 3.0F, 2.0F);
    }
}
