package com.efkrdnz.magical.magic.skill.voidschool;

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
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * VOID T0 - SILENCE / RELEASED_FLOCK / CLOUD. A flock of ink moths drifts from the hand, veers
 * toward the nearest hostile and clings around its head; everything inside the flock is hushed
 * (cannot cast) and nibbled. Sneak = hold the flock as a hushing halo around yourself.
 */
public final class HushwingSkill implements SkillModule {
    /**
     * Draw mode for a flock with the caster's own eyes inside it: the halo, always, and a thrown
     * flock until it is clear of their face (see thicken). A flock out on its own is draw mode 0.
     */
    private static final int DRAW_SPARSE = 1;
    /** How far past its own radius from the caster's eyes a thrown flock is clear of their face. */
    private static final double CLEAR_OF_FACE = 0.8D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.HUSHWING;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 start = ctx.eye().add(ctx.look().scale(0.8D));
                SpellEffectEntity flock = SpellEffectEntity.spawn(ctx, ctx.sneak() ? ctx.feet().add(0.0D, 1.0D, 0.0D) : start, Math.max(40, ctx.duration()), 1.6F * Math.max(0.5F, ctx.size()), ctx.look());
                // drawing only: every flock starts with the caster inside it (see thicken); the sneak
                // bit below the draw mode is untouched
                flock.setMode((byte) (flock.mode() | (DRAW_SPARSE << 1)));
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(2.0F, 12.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            Entity owner = entity.owner();
            if (entity.sneakMode()) {
                if (!(owner instanceof LivingEntity living) || !living.isAlive()) {
                    entity.finish();
                    return;
                }
                entity.setPos(living.getX(), living.getY() + 1.1D, living.getZ());
            } else {
                Entity target = entity.target();
                if (entity.tickCount % 10 == 0) {
                    List<LivingEntity> near = SkillTargets.hostilesWithin(level, owner, entity.position(), 7.0D);
                    entity.setTarget(near.isEmpty() ? null : near.get(0));
                    target = entity.target();
                }
                double step = 0.32D * Math.max(0.3D, entity.speed() * 3.0D);
                if (target instanceof LivingEntity living && living.isAlive()) {
                    Vec3 head = living.getEyePosition();
                    Vec3 to = head.subtract(entity.position());
                    if (to.length() > 1.2D) {
                        entity.setPos(entity.position().add(to.normalize().scale(step)));
                    } else {
                        entity.setPos(head.add(0.0D, -0.2D, 0.0D));
                    }
                    if (to.lengthSqr() > 1.0E-4D) {
                        entity.setDirection(to.normalize());
                    }
                } else {
                    entity.setPos(entity.position().add(entity.direction().scale(step)));
                }
                thicken(entity, owner);
            }
            shedAsh(level, entity);
            for (LivingEntity hostile : SkillTargets.hostilesWithin(level, owner, entity.position(), entity.radius())) {
                MagicStatusService.apply(hostile, MagicStatus.SILENCED, 12, entity.definition().id(), owner);
                if (entity.tickCount % 5 == 0) {
                    SkillTargets.hurt(level, owner, hostile, entity.damage(), entity.definition().id());
                    // the nibble: a breath of ink curling off the hushed mouth. A wisp rises and
                    // doubles as it goes, so at 2.4 every nibble left a navy card a block across
                    // over the victim's head, and a flock nibbles eight times before it is done
                    Vec3 mouth = hostile.getEyePosition();
                    level.sendParticles(new TintedParticleOptions(MagicalParticles.WISP.get(), VisualProfiles.of(entity.definition()).color(ColorRole.INK), 1.6F),
                            mouth.x, mouth.y - 0.15D, mouth.z, 1, 0.15D, 0.1D, 0.15D, 0.01D);
                }
            }
        };
    }

    /**
     * Drawing only. A thrown flock leaves from just ahead of the caster's eyes with the caster
     * inside its cloud, so it is drawn sparse until it is clear of their face and then thickens to
     * its full count; the sparse cloud's moths are the first of the full one's, so the rest join it
     * rather than the flock changing. The sneak bit is kept as it is.
     */
    private static void thicken(SpellEffectEntity entity, Entity owner) {
        if (entity.effectDrawMode() != DRAW_SPARSE) {
            return;
        }
        if (owner == null || entity.position().distanceTo(owner.getEyePosition()) > entity.radius() + CLEAR_OF_FACE) {
            entity.setMode((byte) (entity.mode() & 1));
        }
    }

    /**
     * The moths are drawn by the swarm; the dust they shed is real ash, drifting down behind the
     * flock. Held back for the first ticks of a thrown flock, while it is still in the caster's
     * face, and laid only round the rim of a held halo, never across the caster's own eyes.
     */
    private static void shedAsh(ServerLevel level, SpellEffectEntity entity) {
        if (entity.tickCount % 2 != 0) {
            return;
        }
        double r = entity.radius();
        if (entity.sneakMode()) {
            if (entity.tickCount % 4 != 0) {
                return;
            }
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            level.sendParticles(ParticleTypes.ASH, entity.getX() + Math.cos(a) * r, entity.getY() + (level.random.nextDouble() - 0.5D) * 0.6D, entity.getZ() + Math.sin(a) * r, 1, 0.1D, 0.1D, 0.1D, 0.0D);
        } else if (entity.tickCount > 4) {
            level.sendParticles(ParticleTypes.ASH, entity.getX(), entity.getY(), entity.getZ(), 2, r * 0.35D, r * 0.25D, r * 0.35D, 0.0D);
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.VOID)
                .circle(CircleScript.of(SchoolMaterial.VOID).emblem(EmblemId.MOTH).frame(6).band(GlyphKind.WAVE_BAND, 9, ColorRole.DIM).band(GlyphKind.DASHED_RING, 18).stamps(StampId.FEATHER, 6).core(CoreKind.RIPPLE, ColorRole.INK).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                // the moths themselves; the ash they shed is real (see shedAsh). Sixteen moths left the
                // thrown flock a scatter of five or six flakes by the time it settled on a head, so it
                // is 26 (a swarm is drawn at the flock's synced radius, whatever size is written here)
                .silhouette(Silhouette.swarm(Silhouette.Form.CLOUD, FxKinds.Smoke.ASH_FLAKE, 26, 1.6F).withRole(ColorRole.INK).forModes(0))
                // with the caster's own eyes inside it - the held halo, and a thrown flock still
                // leaving the hand - it is the sparse sixteen
                .silhouette(Silhouette.swarm(Silhouette.Form.CLOUD, FxKinds.Smoke.ASH_FLAKE, 16, 1.6F).withRole(ColorRole.INK).forModes(DRAW_SPARSE))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.INK_STAIN, FxKinds.Smoke.ASH_FLAKE, FxKinds.Overlay.STATIC_GLITCH)
                .budget(1)
                .bounds(3.0F, 3.0F, 2.0F);
    }
}
