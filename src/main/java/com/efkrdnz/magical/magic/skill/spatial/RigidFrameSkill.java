package com.efkrdnz.magical.magic.skill.spatial;

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
 * SPATIAL T2 - LOCK_OFFSET / SELF_RADIUS / RIGID_CONSTELLATION. A telegraph draws hairlines to every
 * hostile nearby; then the frame locks: each victim's world offset from the hub is enforced every
 * tick, so they are carried wherever the caster goes and can never close or retreat. Struts that
 * would push a victim into terrain slide up a little, else snap. Sneak = anchor the hub at the cast
 * point instead of following the caster.
 */
public final class RigidFrameSkill implements SkillModule {
    private static final int TELEGRAPH = 10;
    private static final double RADIUS = 6.0D;
    private static final int MAX_SLOTS = 6;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.RIGID_FRAME;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity hub = SpellEffectEntity.spawn(ctx, ctx.feet().add(0.0D, 1.2D, 0.0D), TELEGRAPH + Math.max(20, ctx.duration()), (float) RADIUS, ctx.look());
                sync(hub, false, List.of());
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

    private static void sync(SpellEffectEntity entity, boolean locked, List<LivingEntity> victims) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Lock", locked);
        int[] ids = new int[victims.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = victims.get(i).getId();
        }
        tag.putIntArray("V", ids);
        entity.setSyncedData(tag);
    }

    private static Vec3 hub(SpellEffectEntity entity, LivingEntity owner) {
        return entity.sneakMode() ? entity.position() : owner.position().add(0.0D, 1.2D, 0.0D);
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
            Vec3 hub = hub(entity, owner);
            if (!entity.sneakMode()) {
                entity.setPos(hub);
            }
            int t = entity.tickCount;
            if (t < TELEGRAPH) {
                if (t % 2 == 0) {
                    sync(entity, false, SkillTargets.hostilesWithin(level, owner, hub, RADIUS));
                }
                return;
            }
            CompoundTag data = entity.serverData();
            if (t == TELEGRAPH) {
                List<LivingEntity> caught = SkillTargets.hostilesWithin(level, owner, hub, RADIUS);
                ListTag slots = new ListTag();
                List<LivingEntity> locked = new ArrayList<>();
                for (LivingEntity victim : caught) {
                    if (locked.size() >= MAX_SLOTS) {
                        break;
                    }
                    Vec3 offset = victim.position().subtract(hub);
                    CompoundTag slot = new CompoundTag();
                    slot.putUUID("Id", victim.getUUID());
                    slot.putDouble("OX", offset.x);
                    slot.putDouble("OY", offset.y);
                    slot.putDouble("OZ", offset.z);
                    slots.add(slot);
                    locked.add(victim);
                    if (victim instanceof Mob mob) {
                        mob.getNavigation().stop();
                    }
                }
                data.put("Slots", slots);
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                sync(entity, true, locked);
                if (locked.isEmpty()) {
                    entity.finish();
                }
                SpellFx.impact(level, entity.definition(), hub, new Vec3(0.0D, 1.0D, 0.0D), null, owner, 1.0F);
                return;
            }
            ListTag slots = data.getList("Slots", Tag.TAG_COMPOUND);
            List<LivingEntity> still = new ArrayList<>();
            boolean changed = false;
            for (int i = 0; i < slots.size(); i++) {
                CompoundTag slot = slots.getCompound(i);
                Entity e = level.getEntity(slot.getUUID("Id"));
                if (!(e instanceof LivingEntity victim) || !victim.isAlive()) {
                    slots.remove(i--);
                    changed = true;
                    continue;
                }
                Vec3 wanted = hub.add(slot.getDouble("OX"), slot.getDouble("OY"), slot.getDouble("OZ"));
                Vec3 clear = SafeSpotSearch.liftClear(level, wanted, victim.getBbWidth(), victim.getBbHeight(), 1.5D);
                if (clear == null) {
                    // the strut snaps
                    SkillTargets.hurt(level, owner, victim, entity.damage(), entity.definition(), true);
                    slots.remove(i--);
                    changed = true;
                    continue;
                }
                victim.setPos(clear.x, clear.y, clear.z);
                victim.setDeltaMovement(Vec3.ZERO);
                victim.fallDistance = 0.0F;
                victim.hurtMarked = true;
                if (victim instanceof Mob mob) {
                    mob.getNavigation().stop();
                }
                still.add(victim);
            }
            if (changed) {
                data.put("Slots", slots);
                sync(entity, true, still);
            }
            if (still.isEmpty()) {
                entity.finish();
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SPATIAL)
                .circle(CircleScript.of(SchoolMaterial.SPATIAL).emblem(EmblemId.ARMATURE).frame(8, CircleScript.FrameStyle.NESTED).band(GlyphKind.TICK_BAND, 24, ColorRole.HOT).band(GlyphKind.CHAIN_BAND, 8).stamps(StampId.SQUARE, 8).spokes(6, 0.35F, true).orbit(3, 0.84F, 4).core(CoreKind.CROSS).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.custom("rigid_frame", 6.0F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.SPARK_STREAK, FxKinds.Overlay.CRACKED_GLASS)
                .bounds(8.0F, 4.0F, 4.0F);
    }
}
