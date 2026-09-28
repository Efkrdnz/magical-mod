package com.efkrdnz.magical.magic.skill.light;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.entity.fx.ThrownSpellEntity;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * LIGHT T0 - BLIND / LOB_AIRBURST / LOS_SPHERE. A lens-bead lobbed under gravity that airbursts
 * after its fuse (or second contact): everything with line of sight to the burst is hurt and
 * dazzled; cover blocks it. A throw-over-the-wall flashbang. Sneak = lob backward.
 */
public final class GlintSkill implements SkillModule {
    /** Firework sparks in the airburst's shell: the flashbang's crackle. */
    private static final int BURST_SPARKS = 18;

    /** How far below level the shell's lowest sparks aim, as the sine of the angle: a bead bursts near the floor. */
    private static final double SHELL_FLOOR = -0.35D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.GLINT;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 dir = ctx.lookOrBack();
                Vec3 start = ctx.eye().add(dir.scale(0.5D)).add(0.0D, -0.15D, 0.0D);
                int fuse = Math.max(6, ctx.duration());
                SpellEffectEntity template = SpellEffectEntity.create(ctx.level(), ctx.definition(), ctx.stats(), ctx.caster(), start, fuse + 40, 5.5F * Math.max(0.5F, ctx.size()), dir, (int) (ctx.seed() & 63));
                template.setMode(ctx.sneak() ? (byte) 1 : (byte) 0);
                ThrownSpellEntity bead = ThrownSpellEntity.create(ctx.level(), template, start, dir.scale(Math.max(0.4D, ctx.stats().speed())).add(0.0D, 0.1D, 0.0D), 0.05F, 0.99F, 1, fuse);
                ctx.level().addFreshEntity(bead);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(3.0F, 14.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            if (!(entity instanceof ThrownSpellEntity bead) || !bead.landed()) {
                return;
            }
            ServerLevel level = bead.serverLevel();
            Vec3 burst = bead.position();
            for (LivingEntity hit : SkillTargets.hostilesWithin(level, bead.owner(), burst, bead.radius())) {
                HitResult los = level.clip(new ClipContext(burst, hit.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, bead));
                if (los.getType() == HitResult.Type.BLOCK) {
                    continue;
                }
                SkillTargets.hurt(level, bead.owner(), hit, bead.damage(), bead.definition(), true);
                MagicStatusService.apply(hit, MagicStatus.DAZZLED, 40, bead.definition().id(), bead.owner());
                hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
            SpellFx.impact(level, bead.definition(), burst, new Vec3(0.0D, 1.0D, 0.0D), null, bead.owner(), 2.2F);
            flash(level, burst.add(0.0D, 0.2D, 0.0D));
            bead.discard();
        };
    }

    /**
     * The airburst itself. Once the matter layer took the shader's white bloom away, the flash of a
     * flashbang was a spark and a handful of motes, and by the time anyone looked it read as nothing
     * had gone off. This is a firework's crackle instead: a dome of sparks thrown out round the bead
     * that hangs and settles for a couple of seconds, and a few glints left burning at the heart.
     * The dome leans up, because the bead bursts at the floor and a spark thrown down lands at once.
     */
    private static void flash(ServerLevel level, Vec3 at) {
        double spin = level.random.nextDouble() * Math.PI * 2.0D;
        double golden = Math.PI * (3.0D - Math.sqrt(5.0D));
        for (int i = 0; i < BURST_SPARKS; i++) {
            double y = 1.0D - (i + 0.5D) / BURST_SPARKS * (1.0D - SHELL_FLOOR);
            double r = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
            double a = spin + i * golden;
            // a count of 0 sends one particle with the offsets as its velocity, times the speed; a
            // firework spark keeps 0.91 of its speed a tick, so 0.22 carries it about two and a half blocks
            level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 0, Math.cos(a) * r, y, Math.sin(a) * r, 0.22D);
        }
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 6, 0.4D, 0.3D, 0.4D, 0.02D);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.LIGHT)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.STAR6).frame(6).band(GlyphKind.DASHED_RING, 24).stamps(StampId.NEEDLE, 6).core(CoreKind.DISC_GLOW).spin(SpinSignature.SINGLE_FAST))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.LENS_STREAKS, 0.5F, 4, 10))
                .trail(new ProfileCues.TrailSpec(FxKinds.Smoke.SPARK_STREAK, 2, 0.08F, 10, 0.2F, 0, 1.0F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.LENS_SPARKLE, FxKinds.Overlay.FLASH)
                .bounds(2.0F, 2.0F, 1.0F);
    }
}
