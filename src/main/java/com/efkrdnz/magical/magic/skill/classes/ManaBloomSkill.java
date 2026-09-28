package com.efkrdnz.magical.magic.skill.classes;

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
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.TrailParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * EVOKER - SLOW_BLOOM / AIMED_POINT / GATHER_THEN_BURST. A bud of raw mana opens where you point
 * it, dragging everything nearby steadily inward for the whole bloom, and only pays out when it
 * finally opens. It is the Evoker skill because it is the one spell in the tree that rewards
 * casting early and trusting it. Sneak = a tighter, faster bloom.
 */
public final class ManaBloomSkill implements SkillModule {
    private static final double PULL = 0.06D;
    private static final int PULL_INTERVAL = 4;
    /** Motes drawn in off the rim of the pull each time it pulls. */
    private static final int GATHER_MOTES = 2;
    /** How long a mote takes to reach the bud from wherever it starts. */
    private static final int GATHER_TICKS = 10;
    /** Petals shed off the bud each pull: how far round it, how high on it, and when it sheds two. */
    private static final double SHED_SPREAD = 0.55D;
    private static final double SHED_LIFT = 0.6D;
    private static final float SHED_HALF_OPEN = 0.5F;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.MANA_BLOOM;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                int life = ctx.sneak() ? Math.max(20, ctx.duration() / 2) : Math.max(40, ctx.duration());
                SpellEffectEntity bud = SpellEffectEntity.spawn(ctx, ctx.aim().point(), life, ctx.size(), ctx.look());
                bud.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                // no release here: the casting service plays it on SUCCESS, and a second one doubled the
                // muzzle flash, the burst, the sound and the caster's bloom wash
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 26.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(5.0F, 22.0F);
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
                float openness = Math.min(1.0F, entity.tickCount / (float) Math.max(1, entity.life()));
                entity.setValue(openness);
                if (entity.tickCount % PULL_INTERVAL != 0) {
                    return;
                }
                ServerLevel level = entity.serverLevel();
                Vec3 centre = entity.position();
                TrailParticleOption thread = new TrailParticleOption(centre, VisualProfiles.of(entity.definition()).color(ColorRole.BASE), GATHER_TICKS);
                gather(level, thread, centre, entity.radius() * 2.0D);
                shed(level, centre, openness);
                // The pull tightens as the bloom opens, so late escapes are harder than early ones.
                double strength = PULL * (0.5D + openness);
                for (LivingEntity victim : SkillTargets.hostilesWithin(level, entity.owner(), centre, entity.radius() * 2.0D)) {
                    Vec3 toward = centre.subtract(victim.position());
                    if (toward.lengthSqr() < 0.04D) {
                        continue;
                    }
                    victim.setDeltaMovement(victim.getDeltaMovement().add(toward.normalize().scale(strength)));
                    victim.hurtMarked = true;
                    // whatever is being dragged is tied to the bud by a thread of its own
                    Vec3 body = victim.getBoundingBox().getCenter();
                    level.sendParticles(thread, body.x, body.y, body.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                Vec3 centre = entity.position();
                for (LivingEntity victim : SkillTargets.hostilesWithin(level, entity.owner(), centre, entity.radius() * 1.5D)) {
                    SkillTargets.hurt(level, entity.owner(), victim, entity.damage(), entity.definition(), true);
                    SkillTargets.shove(victim, centre, 0.7D, 0.3D);
                }
                SpellFx.impact(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 1.6F);
            }
        };
    }

    /**
     * The pull made visible: motes of the bud's own colour drawn in off the rim of its reach, a
     * few every pull. Vanilla's trail particle flies to the point it is handed, so each one lands
     * in the bud wherever on the rim it starts - which is also where the pull stops reaching.
     */
    private static void gather(ServerLevel level, TrailParticleOption thread, Vec3 centre, double reach) {
        for (int i = 0; i < GATHER_MOTES; i++) {
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            double y = centre.y + (level.random.nextDouble() - 0.35D) * 1.4D;
            level.sendParticles(thread, centre.x + Math.cos(a) * reach, y, centre.z + Math.sin(a) * reach, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /**
     * The bud coming apart as it opens: real cherry petals loosed off it at every pull, one while it
     * is young and two once it is half open, drifting down round it the way a tree sheds. They were
     * a ring of additive petals before, and fourteen of those at noon stacked into a white blot.
     */
    private static void shed(ServerLevel level, Vec3 centre, float openness) {
        int petals = openness < SHED_HALF_OPEN ? 1 : 2;
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, centre.x, centre.y + SHED_LIFT, centre.z, petals,
                SHED_SPREAD, SHED_SPREAD * 0.4D, SHED_SPREAD, 0.0D);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .palette(3)
                // a bloom: its matter is petals, cherry ones, and they are what the opening throws
                .accent(Accent.BLOOM)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.LEAF).frame(14).band(GlyphKind.PETAL_BAND, 20).band(GlyphKind.BRAID_BAND, 5).stamps(StampId.TEARDROP, 10).core(CoreKind.RIPPLE).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.orb(Silhouette.Form.SPHERE, FxKinds.Orb.CHARGE_SPHERE, 0.9F, 1, 3))
                // no petal swarm: the petals are real ones now, shed off the bud by behaviour()
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.SPIRAL_DRAIN, FxKinds.Smoke.RUNE_MOTE, FxKinds.Overlay.PRISM_RING)
                .bounds(4.0F, 3.0F, 1.4F);
    }
}
