package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.entity.fx.WrenchedItemEntity;
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
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * WARRIOR - DISARM / MELEE_REACH / SINGLE_TARGET. A double-helix coil bores forward from the forearm
 * over three ticks and clamps the first living thing it reaches: staggered and DISARMED, its
 * main-hand item wrenched out to hover under the sigil before flying home. Sneak = the off-hand.
 */
public final class SunderGripSkill implements SkillModule {
    private static final int LUNGE = 3;
    private static final int LIFE = 12;
    private static final double REACH = 3.2D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SUNDER_GRIP;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity coil = SpellEffectEntity.spawn(ctx, ctx.eye().add(0.0D, -0.3D, 0.0D), LIFE, 0.2F, ctx.look());
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(0.0F, 3.0F);
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
            LivingEntity owner = entity.livingOwner();
            if (owner == null || !owner.isAlive()) {
                entity.finish();
                return;
            }
            Vec3 origin = owner.getEyePosition().add(0.0D, -0.3D, 0.0D);
            Vec3 dir = owner.getLookAngle();
            entity.setPos(origin);
            entity.setDirection(dir);
            int t = entity.tickCount;
            float extension = (float) (t <= LUNGE ? REACH * t / LUNGE : Math.max(0.0D, REACH * (1.0D - (t - LUNGE - 4) / 5.0D)));
            entity.setRadius(Math.max(0.2F, extension));
            if (t > LUNGE || entity.phase() == SpellEffectEntity.PHASE_ACTIVE) {
                return;
            }
            Vec3 tip = origin.add(dir.scale(extension));
            AABB capsule = new AABB(origin, tip).inflate(1.0D);
            List<LivingEntity> hits = SkillTargets.hostilesIn(level, owner, capsule);
            hits.sort((x, y) -> Double.compare(x.distanceToSqr(origin), y.distanceToSqr(origin)));
            for (LivingEntity victim : hits) {
                Vec3 p = victim.getBoundingBox().getCenter();
                double along = p.subtract(origin).dot(dir);
                if (along < 0.0D || along > extension + 0.5D || p.distanceTo(origin.add(dir.scale(along))) > 1.0D) {
                    continue;
                }
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                entity.setRadius((float) Math.max(0.5D, along));
                SkillTargets.hurt(level, owner, victim, entity.damage(), entity.definition(), true);
                victim.setDeltaMovement(Vec3.ZERO);
                victim.hurtMarked = true;
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 2));
                if (victim instanceof Mob mob) {
                    mob.getNavigation().stop();
                }
                EquipmentSlot slot = entity.sneakMode() ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
                ItemStack held = victim.getItemBySlot(slot);
                if (!held.isEmpty()) {
                    Vec3 hover = origin.add(dir.scale(2.5D));
                    WrenchedItemEntity wrenched = WrenchedItemEntity.create(level, entity.definition(), victim, held.copy(), slot == EquipmentSlot.OFFHAND, hover, Math.max(40, entity.duration()), entity.seed());
                    victim.setItemSlot(slot, ItemStack.EMPTY);
                    level.addFreshEntity(wrenched);
                    SpellFx.impact(level, entity.definition(), hover, dir.scale(-1.0D), null, owner, 1.0F);
                }
                break;
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .palette(2)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.VICE).frame(12).band(GlyphKind.BRAID_BAND, 8).band(GlyphKind.TICK_BAND, 32).stamps(StampId.GEAR, 4).core(CoreKind.CROSS).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.filament(Silhouette.Form.HELIX, FxKinds.Filament.HELIX, 2, 0.28F, 3.2F, 1).forModes(0))
                .silhouette(Silhouette.glyph(0.5F).forModes(1))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.CLOCK_SPOKES, FxKinds.Smoke.SPARK_STREAK, FxKinds.Overlay.CRACKED_GLASS)
                .bounds(4.0F, 2.0F, 2.0F);
    }
}
