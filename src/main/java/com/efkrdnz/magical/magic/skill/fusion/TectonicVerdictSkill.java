package com.efkrdnz.magical.magic.skill.fusion;

import com.efkrdnz.magical.entity.fx.SolidConstructEntity;
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
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * MAGIC ORIGINATOR (magma_vent + crease_fold) - HINGE_CRUSH / AIM_SURFACE_SEAM / TWO_RISING_PLATES.
 * A molten seam is scored on the ground; after a counterable windup two solid rock plates hinge up
 * on either side, sliding everything on them toward the seam until the plates meet and crush, hold
 * the jaws closed a while, then drop flat. Sneak = seam under your own feet (you are exempt).
 */
public final class TectonicVerdictSkill implements SkillModule {
    private static final int WINDUP = 24;
    private static final int RISE = 20;
    private static final int HOLD = 40;
    private static final double HALF_LENGTH = 7.0D;
    private static final double PLATE = 7.0D;
    private static final int STEPS = 3;
    private static final byte MODE_PLATE = 2;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.TECTONIC_VERDICT;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 look = ctx.look();
                Vec3 flat = new Vec3(look.x, 0.0D, look.z);
                flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
                Vec3 seam = ctx.sneak() ? ctx.feet() : ctx.aim().point();
                SpellEffectEntity controller = SpellEffectEntity.spawn(ctx, seam, WINDUP + RISE + HOLD + 10, (float) HALF_LENGTH, flat);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 20.0D;
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
            public int counterWindowTicks() {
                return WINDUP;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(4.0F, 20.0F);
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
                if ((entity.mode() & MODE_PLATE) != 0) {
                    return;
                }
                ServerLevel level = entity.serverLevel();
                LivingEntity owner = entity.livingOwner();
                Vec3 seam = entity.position();
                Vec3 f = entity.direction();
                Vec3 axis = new Vec3(-f.z, 0.0D, f.x);
                int t = entity.tickCount;
                if (t < WINDUP) {
                    return;
                }
                CompoundTag data = entity.serverData();
                if (t == WINDUP) {
                    // two plates, each approximated by three stepped boxes that rise with the hinge
                    ListTag plates = new ListTag();
                    for (int side = -1; side <= 1; side += 2) {
                        for (int step = 0; step < STEPS; step++) {
                            double along = (step + 0.5D) * PLATE / STEPS * side;
                            Vec3 pos = seam.add(f.scale(along));
                            SolidConstructEntity box = SolidConstructEntity.create(level, entity, pos, (float) (PLATE / STEPS), 0.6F, 600.0F, side < 0 ? step : STEPS + step);
                            box.setMode(MODE_PLATE);
                            box.setLife(RISE + HOLD + 10);
                            box.setDirection(axis);
                            box.setSolid(false);
                            level.addFreshEntity(box);
                            CompoundTag tag = new CompoundTag();
                            tag.putUUID("Id", box.getUUID());
                            tag.putInt("Side", side);
                            tag.putInt("Step", step);
                            plates.add(tag);
                        }
                    }
                    data.put("Plates", plates);
                    entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                    SpellFx.impact(level, entity.definition(), seam, new Vec3(0.0D, 1.0D, 0.0D), null, owner, 2.0F);
                }
                int since = t - WINDUP;
                float angle = (float) Math.toRadians(Math.min(1.0D, since / (double) RISE) * 80.0D);
                entity.setValue(angle);
                ListTag plates = data.getList("Plates", Tag.TAG_COMPOUND);
                for (int i = 0; i < plates.size(); i++) {
                    CompoundTag tag = plates.getCompound(i);
                    if (!(level.getEntity(tag.getUUID("Id")) instanceof SolidConstructEntity box)) {
                        continue;
                    }
                    int side = tag.getInt("Side");
                    double along = (tag.getInt("Step") + 0.5D) * PLATE / STEPS;
                    // the hinge lifts the far end: each step box rises along the tilted plate
                    double lifted = Math.sin(angle) * along;
                    double forward = Math.cos(angle) * along;
                    Vec3 pos = seam.add(f.scale(forward * side)).add(0.0D, lifted, 0.0D);
                    box.setPos(pos.x, pos.y, pos.z);
                    box.setValue(angle * side);
                    box.setSolid(since > 2);
                }
                if (since <= RISE) {
                    // slide everything on either plate toward the seam
                    AABB region = new AABB(seam, seam).inflate(HALF_LENGTH + 1.0D, 6.0D, HALF_LENGTH + 1.0D);
                    for (LivingEntity victim : SkillTargets.hostilesIn(level, owner, region)) {
                        Vec3 rel = victim.position().subtract(seam);
                        double d = rel.dot(f);
                        double lateral = Math.abs(rel.dot(axis));
                        if (Math.abs(d) > PLATE || lateral > HALF_LENGTH) {
                            continue;
                        }
                        Vec3 pull = f.scale(-Math.signum(d) * 0.35D);
                        victim.setDeltaMovement(pull.x, Math.max(victim.getDeltaMovement().y, 0.05D), pull.z);
                        victim.hurtMarked = true;
                    }
                    if (since == RISE) {
                        // the plates meet: crush
                        for (LivingEntity victim : SkillTargets.hostilesIn(level, owner, region)) {
                            Vec3 rel = victim.position().subtract(seam);
                            if (Math.abs(rel.dot(f)) > 1.4D || Math.abs(rel.dot(axis)) > HALF_LENGTH) {
                                continue;
                            }
                            SkillTargets.hurt(level, owner, victim, entity.damage(), entity.definition(), true);
                            victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
                            MagicStatusService.apply(victim, MagicStatus.ROOTED, HOLD, entity.definition().id(), owner);
                        }
                        SpellFx.impact(level, entity.definition(), seam.add(0.0D, 1.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D), null, owner, 2.5F);
                    }
                    return;
                }
                if (since < RISE + HOLD && since % 10 == 0) {
                    AABB jaws = new AABB(seam, seam).inflate(1.4D, 5.0D, 1.4D).inflate(Math.abs(axis.x) * HALF_LENGTH, 0.0D, Math.abs(axis.z) * HALF_LENGTH);
                    for (LivingEntity pinned : SkillTargets.hostilesIn(level, owner, jaws)) {
                        SkillTargets.hurt(level, owner, pinned, 4.0F, entity.definition().id());
                    }
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                if ((entity.mode() & MODE_PLATE) != 0) {
                    return;
                }
                ServerLevel level = entity.serverLevel();
                ListTag plates = entity.serverData().getList("Plates", Tag.TAG_COMPOUND);
                for (int i = 0; i < plates.size(); i++) {
                    if (level.getEntity(plates.getCompound(i).getUUID("Id")) instanceof SolidConstructEntity box) {
                        box.finish();
                    }
                }
                SpellFx.impact(level, entity.definition(), entity.position(), new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 1.6F);
                SpellFx.decal(level, entity.definition(), entity.position(), new Vec3(0.0D, 1.0D, 0.0D), 3.0F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SPATIAL)
                .palette(2)
                .circle(CircleScript.of(SchoolMaterial.SPATIAL).emblem(EmblemId.JAWS).frame(9).band(GlyphKind.TOOTH_BAND, 28, ColorRole.HOT).band(GlyphKind.CHAIN_BAND, 14).band(GlyphKind.TICK_BAND, 56, ColorRole.DIM).stamps(StampId.DIAMOND, 9).mirror(1).orbit(7, 0.86F, 4).core(CoreKind.CROSS).stack(3, 0.6F).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.AIM_SURFACE)
                .throughTerrain(true)
                .silhouette(Silhouette.custom("tectonic_seam", 7.0F).forModes(0))
                .silhouette(Silhouette.custom("tectonic_plate", 7.0F).forModes(1))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_SURGE)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.DUST, FxKinds.Overlay.CRACKED_GLASS)
                .budget(3)
                .bounds(10.0F, 8.0F, 2.0F);
    }
}
