package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.status.MagicStatus;
import com.efkrdnz.magical.magic.status.MagicStatusService;
import com.efkrdnz.magical.magic.visual.Accent;
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
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * WARRIOR - DASH_BULLDOZE / SELF_TRAVEL / LINE_PATH. The caster surges along the horizontal look with
 * an arcane ram-wedge at the chest, throwing everything it overlaps ASIDE and staying knockback
 * immune. Stopped by solids. Sneak = charge backward.
 */
public final class IronChargeSkill implements SkillModule {
    /**
     * Crumbs of the ground kicked up from the feet each tick of the charge. Two, because the
     * accent's own wake already puts a puff behind the wedge every tick it moves.
     */
    private static final int KICK_CRUMBS = 2;
    /** Crit sparks off a body the wedge throws aside. */
    private static final int RAM_SPARKS = 12;
    /** Crumbs of the ground torn up from under the feet of a body the wedge throws aside. */
    private static final int RAM_CRUMBS = 8;
    /** Low puffs of dust where those feet skid. */
    private static final int RAM_DUST = 3;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.IRON_CHARGE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 look = ctx.lookOrBack();
                Vec3 flat = new Vec3(look.x, 0.0D, look.z);
                flat = flat.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 1.0D) : flat.normalize();
                SpellEffectEntity wedge = SpellEffectEntity.spawn(ctx, ctx.feet().add(0.0D, 1.1D, 0.0D), Math.max(4, ctx.duration()), 0.7F, flat);
                MagicStatusService.apply(ctx.caster(), MagicStatus.IMMOVABLE, Math.max(4, ctx.duration()) + 1, ctx.definition().id(), ctx.caster());
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(3.0F, 10.0F);
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
            Vec3 dir = entity.direction();
            double speed = Math.max(0.6D, entity.speed());
            if (owner.horizontalCollision && entity.tickCount > 1) {
                entity.finish();
                return;
            }
            owner.setDeltaMovement(dir.x * speed, owner.onGround() ? 0.02D : 0.0D, dir.z * speed);
            owner.fallDistance = 0.0F;
            owner.hurtMarked = true;
            entity.setPos(owner.getX(), owner.getY() + 1.1D, owner.getZ());
            if (owner.onGround()) {
                kickUp(level, owner);
            }
            Vec3 nose = owner.position().add(dir.scale(0.7D));
            AABB wedge = new AABB(nose.x - 0.7D, owner.getY(), nose.z - 0.7D, nose.x + 0.7D, owner.getY() + owner.getBbHeight(), nose.z + 0.7D).inflate(0.2D);
            CompoundTag data = entity.serverData();
            for (LivingEntity hit : SkillTargets.hostilesIn(level, owner, wedge)) {
                String key = "hit_" + hit.getId();
                if (data.getBoolean(key)) {
                    continue;
                }
                data.putBoolean(key, true);
                SkillTargets.hurt(level, owner, hit, entity.damage(), entity.definition(), true);
                Vec3 side = new Vec3(-dir.z, 0.0D, dir.x);
                double sign = hit.position().subtract(owner.position()).dot(side) >= 0.0D ? 1.0D : -1.0D;
                double kb = 0.8D * Math.max(0.5D, entity.knockback());
                hit.setDeltaMovement(side.scale(sign * kb).add(dir.scale(0.2D)).add(0.0D, 0.3D, 0.0D));
                hit.hurtMarked = true;
                ram(level, hit);
            }
        };
    }

    /**
     * The ground under a charge: crumbs of whatever the caster is running on, thrown up from the
     * feet every tick they are on it. The wedge at the chest is the light; this is the weight behind
     * it, and the caster outruns it at once, so none of it hangs in their own view.
     */
    private static void kickUp(ServerLevel level, LivingEntity runner) {
        BlockState under = runner.getBlockStateOn();
        if (under.isAir()) {
            return;
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), runner.getX(), runner.getY() + 0.1D, runner.getZ(),
                KICK_CRUMBS, 0.3D, 0.0D, 0.3D, 0.1D);
    }

    /**
     * A body the wedge throws aside: vanilla's crit sparks where it struck, and the ground torn up
     * under its feet as they are knocked out from under it. The charge sends no impact cue, so
     * without this a hit read as nothing but the red flash.
     *
     * <p>The dust is at the feet, not the chest. A ram is point-blank by definition - the caster is
     * standing in the body when it lands - so poofs at the middle of it were white cards a block
     * from the caster's eyes, and a poof rises and lingers for up to four seconds.
     */
    private static void ram(ServerLevel level, LivingEntity hit) {
        Vec3 at = hit.getBoundingBox().getCenter();
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, RAM_SPARKS, 0.3D, 0.35D, 0.3D, 0.35D);
        BlockState under = hit.getBlockStateOn();
        if (!under.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, under), hit.getX(), hit.getY() + 0.1D, hit.getZ(),
                    RAM_CRUMBS, 0.35D, 0.05D, 0.35D, 0.15D);
        }
        level.sendParticles(ParticleTypes.DUST_PLUME, hit.getX(), hit.getY() + 0.05D, hit.getZ(), RAM_DUST, 0.3D, 0.0D, 0.3D, 0.02D);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .palette(1)
                // a bull rush: the wake behind it is dust thrown off the ground, not lifting runes
                .accent(Accent.EARTH)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.RAM).frame(3).band(GlyphKind.DASHED_RING, 24, ColorRole.HOT).band(GlyphKind.TICK_BAND, 12).stamps(StampId.CHEVRON, 3).core(CoreKind.CROSS).spin(SpinSignature.ONE_WAY_FAST))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.filament(Silhouette.Form.CROSSED_BLADES, FxKinds.Filament.BLADE_RIM, 2, 0.5F, 1.4F, 2))
                .trail(new ProfileCues.TrailSpec(FxKinds.Smoke.SPARK_STREAK, 3, 0.12F, 8, 0.15F, 0, 0.0F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.SPARK_STREAK, FxKinds.Overlay.SHOCK_RING)
                .bounds(3.0F, 2.0F, 1.5F);
    }
}
