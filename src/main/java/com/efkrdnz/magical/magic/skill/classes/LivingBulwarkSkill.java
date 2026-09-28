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
import com.efkrdnz.magical.magic.visual.Accent;
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
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * WARDEN (BARRIER) - IMMOVABLE / SELF_STANCE / CASTER_BODY_WALL. The caster's body becomes a wall:
 * every external impulse is cancelled, non-allies touching it are stopped and ejected, and every
 * hit taken restores a little barrier. Sneak = plant the stance (rooted, can still turn and cast).
 */
public final class LivingBulwarkSkill implements SkillModule {
    private static final ResourceLocation SPEED_MODIFIER = ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "living_bulwark");
    /** Pillars of dust off the floor as the stance sets. */
    private static final int SETTLE_DUST = 12;
    /**
     * Where they go up: a ring clear of the body, round the sides and the back. A dust pillar is
     * thrown up to three blocks at half a block a tick, and thrown from the feet, as they were, the
     * ones in front climbed straight through the caster's own view; the gap in front is wider than
     * half the widest field of view there is (68 degrees at 110 on a wide screen), so from inside
     * the caster's head the pillars go up out of frame and everybody else sees the whole ring.
     */
    private static final double SETTLE_RING = 1.6D;
    private static final double SETTLE_FRONT_GAP = Math.toRadians(75.0D);
    /** Grit shed by the stone body, and how often. */
    private static final int GRIT = 2;
    private static final int GRIT_INTERVAL = 10;

    /** The body sets like a block dropped on the floor: pillars of dust off whatever it stands on. */
    private static void settle(ServerLevel level, LivingEntity owner) {
        BlockState floor = level.getBlockState(owner.blockPosition().below());
        if (floor.isAir() || !floor.getFluidState().isEmpty()) {
            return;
        }
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.DUST_PILLAR, floor);
        Vec3 look = owner.getLookAngle();
        double facing = Math.atan2(look.z, look.x);
        double arc = Math.PI * 2.0D - SETTLE_FRONT_GAP * 2.0D;
        for (int i = 0; i < SETTLE_DUST; i++) {
            double a = facing + SETTLE_FRONT_GAP + arc * (i + 0.5D) / SETTLE_DUST;
            // count 0: one pillar each, sent straight up; the pillar's own provider adds its throw
            level.sendParticles(dust, owner.getX() + Math.cos(a) * SETTLE_RING, owner.getY() + 0.05D, owner.getZ() + Math.sin(a) * SETTLE_RING, 0, 0.0D, 1.0D, 0.0D, 0.15D);
        }
    }

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
                    settle(entity.serverLevel(), owner);
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
                if (entity.tickCount % GRIT_INTERVAL == 0) {
                    // a body of stone sheds a little grit, low on it and out of its own view
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE_BRICKS.defaultBlockState()), owner.getX(), owner.getY() + 0.6D, owner.getZ(), GRIT, 0.3D, 0.15D, 0.3D, 0.0D);
                }
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
                // the caster is masonry now: what bumps into it throws dust, not runes
                .accent(Accent.EARTH)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.KEYSTONE).frame(10).band(GlyphKind.FACET_BAND, 14).band(GlyphKind.TICK_BAND, 28).stamps(StampId.SQUARE, 7).core(CoreKind.HEX_LENS).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.custom("living_bulwark", 1.0F))
                .release(ReleaseMode.SLAM, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.CRACK_WEB, FxKinds.Smoke.DUST, FxKinds.Overlay.CRACKED_GLASS)
                .bounds(2.0F, 3.0F, 0.5F);
    }
}
