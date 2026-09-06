package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.MagicalMod;
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
import com.efkrdnz.magical.magic.visual.CoreKind;
import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.ReleaseMode;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * WARDEN (BARRIER) - IMMOVABLE / SELF_STANCE / CASTER_BODY_WALL. The caster's body becomes a wall:
 * every external impulse is cancelled, non-allies touching it are stopped and ejected, and every
 * hit taken restores a little barrier. Sneak = plant the stance (rooted, can still turn and cast).
 */
public final class LivingBulwarkSkill implements SkillModule {
    private static final ResourceLocation SPEED_MODIFIER = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "living_bulwark");

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.LIVING_BULWARK;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity stance = SpellEffectEntity.spawn(ctx, ctx.feet(), Math.max(40, ctx.duration()), 1.0F, ctx.look());
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
                return TuningView.UTILITY;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void onSpawn(SpellEffectEntity entity) {
                LivingEntity owner = entity.livingOwner();
                if (owner != null) {
                    AttributeInstance speed = owner.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (speed != null) {
                        speed.removeModifier(SPEED_MODIFIER);
                        speed.addTransientModifier(new AttributeModifier(SPEED_MODIFIER, -0.35D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                    }
                }
            }

            @Override
            public void tick(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                LivingEntity owner = entity.livingOwner();
                if (owner == null || !owner.isAlive()) {
                    entity.finish();
                    return;
                }
                entity.setPos(owner.position());
                Vec3 look = owner.getLookAngle();
                entity.setDirection(new Vec3(look.x, 0.0D, look.z));
                if (entity.tickCount % 10 == 1) {
                    MagicStatusService.apply(owner, MagicStatus.IMMOVABLE, 12, entity.definition().id(), owner);
                    if (entity.sneakMode()) {
                        MagicStatusService.apply(owner, MagicStatus.ROOTED, 12, entity.definition().id(), owner);
                    }
                }
                CompoundTag data = entity.serverData();
                for (LivingEntity hostile : SkillTargets.hostilesIn(level, owner, owner.getBoundingBox().inflate(0.15D))) {
                    Vec3 away = hostile.position().subtract(owner.position());
                    away = new Vec3(away.x, 0.0D, away.z);
                    if (away.lengthSqr() < 1.0E-4D) {
                        away = new Vec3(-look.x, 0.0D, -look.z);
                    }
                    away = away.normalize();
                    hostile.setDeltaMovement(away.scale(0.35D).add(0.0D, 0.05D, 0.0D));
                    hostile.hurtMarked = true;
                    String key = "icd_" + hostile.getId();
                    if (data.getInt(key) > entity.tickCount) {
                        continue;
                    }
                    data.putInt(key, entity.tickCount + 10);
                    SkillTargets.hurt(level, owner, hostile, entity.damage(), entity.definition(), true);
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                LivingEntity owner = entity.livingOwner();
                if (owner != null) {
                    AttributeInstance speed = owner.getAttribute(Attributes.MOVEMENT_SPEED);
                    if (speed != null) {
                        speed.removeModifier(SPEED_MODIFIER);
                    }
                    MagicStatusService.clear(owner, MagicStatus.IMMOVABLE);
                    MagicStatusService.clear(owner, MagicStatus.ROOTED);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .palette(3)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.KEYSTONE).frame(10).band(GlyphKind.FACET_BAND, 14).band(GlyphKind.TICK_BAND, 28).stamps(StampId.SQUARE, 7).core(CoreKind.HEX_LENS).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.custom("living_bulwark", 1.0F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.DUST, FxKinds.Overlay.CRACKED_GLASS)
                .bounds(2.0F, 3.0F, 0.5F);
    }
}
