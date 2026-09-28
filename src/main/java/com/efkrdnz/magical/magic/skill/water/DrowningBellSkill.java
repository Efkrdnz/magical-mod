package com.efkrdnz.magical.magic.skill.water;

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
import com.efkrdnz.magical.magic.visual.CircleAnchor;
import com.efkrdnz.magical.magic.visual.CircleScript;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.CoreKind;
import com.efkrdnz.magical.magic.visual.EmblemId;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.magic.visual.GlyphKind;
import com.efkrdnz.magical.magic.visual.Palette;
import com.efkrdnz.magical.magic.visual.ProfileCues;
import com.efkrdnz.magical.magic.visual.ReleaseMode;
import com.efkrdnz.magical.magic.visual.SchoolMaterial;
import com.efkrdnz.magical.magic.visual.Silhouette;
import com.efkrdnz.magical.magic.visual.SpellFx;
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * WATER T1 - DROWN / AIM_TARGET_HITSCAN / HEAD_SPHERE. A globe of still water seals over the
 * victim's head with its own breath ledger; when it runs dry the victim takes armour-bypassing
 * drowning pulses. The globe is a hittable body anyone else can pop. A miss leaves it hovering,
 * waiting for something to walk into it. Sneak = hold it at arm's length in front of you.
 */
public final class DrowningBellSkill implements SkillModule {
    private static final int BREATH = 300;
    private static final byte MODE_GLOBE = 2;
    /** Ticks between the beads of water set on the globe's underside; each hangs two seconds, then drips. */
    private static final int BEAD_INTERVAL = 3;
    /** Ticks between the bubbles a victim still holding its breath lets go of. */
    private static final int HELD_BREATH_INTERVAL = 10;
    /** The seal: splashes at the head, puffs of spray in a ring off the globe's waist, and drops running off it. */
    private static final int SEAL_SPLASH = 12;
    private static final int SEAL_SPRAY = 5;
    private static final float SEAL_SPRAY_SCALE = 2.2F;
    private static final double SEAL_SPRAY_SPEED = 0.06D;
    private static final int SEAL_RUNOFF = 6;
    /** Within two blocks of the caster's eyes a globe is theirs to look at, not to be rained on by. */
    private static final double ARMS_LENGTH_SQR = 4.0D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.DROWNING_BELL;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                LivingEntity victim = ctx.aim().living();
                boolean valid = victim != null && SkillTargets.isHostile(ctx.caster(), victim) && !ctx.sneak();
                Vec3 pos = valid ? victim.getEyePosition() : ctx.sneak() ? ctx.eye().add(ctx.look().scale(0.9D)) : ctx.aim().point();
                int life = Math.max(40, ctx.duration());
                SpellEffectEntity template = SpellEffectEntity.create(ctx.level(), ctx.definition(), ctx.stats(), ctx.caster(), pos, valid ? life : life + 40, Math.max(0.5F, ctx.size() * 0.5F), ctx.look(), (int) (ctx.seed() & 63));
                template.setMode((byte) (MODE_GLOBE | (ctx.sneak() ? 1 : 0)));
                SolidConstructEntity globe = SolidConstructEntity.create(ctx.level(), template, pos, 0.9F, 0.9F, 1.0F, 0);
                globe.setSolid(false);
                globe.setMode((byte) (MODE_GLOBE | (ctx.sneak() ? 1 : 0)));
                globe.setExtra(BREATH);
                if (valid) {
                    globe.setTarget(victim);
                }
                ctx.level().addFreshEntity(globe);
                if (valid) {
                    seal(ctx.level(), pos, Math.max(0.5F, ctx.size() * 0.5F), VisualProfiles.of(ctx.definition()).color(ColorRole.BRIGHT));
                }
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 14.0D;
            }

            @Override
            public double aimTolerance() {
                return 0.8D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(2.0F, 14.0F);
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
                ServerLevel level = entity.serverLevel();
                Entity target = entity.target();
                Entity owner = entity.owner();
                if (target instanceof LivingEntity victim && victim.isAlive()) {
                    entity.setPos(victim.getEyePosition());
                    int breath = entity.extra() - 5;
                    entity.setExtra(breath);
                    entity.setValue(Math.max(0.0F, breath / (float) BREATH));
                    if (victim instanceof Mob mob) {
                        mob.setTarget(null);
                        mob.getNavigation().stop();
                    }
                    if (victim instanceof ServerPlayer player) {
                        player.setAirSupply(Math.max(-19, Math.round(breath / (float) BREATH * player.getMaxAirSupply())));
                    }
                    if (breath <= 0 && entity.tickCount % 5 == 0) {
                        SkillTargets.hurt(level, owner, victim, entity.damage(), entity.definition().id());
                        victim.invulnerableTime = 0;
                        // the last of the air, gasped out with every pulse
                        level.sendParticles(ParticleTypes.BUBBLE_POP, entity.getX(), entity.getY() + 0.05D, entity.getZ(), 5, 0.12D, 0.12D, 0.12D, 0.02D);
                    } else if (breath > 0 && entity.tickCount % HELD_BREATH_INTERVAL == 0) {
                        level.sendParticles(ParticleTypes.BUBBLE_POP, entity.getX(), entity.getY() + 0.05D, entity.getZ(), 1, 0.08D, 0.05D, 0.08D, 0.01D);
                    }
                    if (entity.tickCount % BEAD_INTERVAL == 0 && still(entity)) {
                        bead(level, entity.position(), entity.radius());
                    }
                    return;
                }
                if (target != null) {
                    entity.finish(); // victim gone
                    return;
                }
                // hovering: at arm's length while sneaking, else waiting at the ray end
                if (entity.sneakMode() && owner instanceof LivingEntity living) {
                    entity.setPos(living.getEyePosition().add(living.getLookAngle().scale(0.9D)));
                } else if (entity.tickCount % BEAD_INTERVAL == 0) {
                    // beads only on a globe left waiting: one held at arm's length moves with the caster, and a bead left hanging where it was reads wrong
                    bead(level, entity.position(), entity.radius());
                }
                for (LivingEntity hostile : SkillTargets.hostilesWithin(level, owner, entity.position(), 0.9D)) {
                    entity.setTarget(hostile);
                    entity.setLife(entity.tickCount + Math.max(40, entity.duration()));
                    SpellFx.impact(level, entity.definition(), hostile.getEyePosition(), new Vec3(0.0D, 1.0D, 0.0D), hostile, owner, 0.7F);
                    break;
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                if (entity.target() instanceof ServerPlayer player) {
                    player.setAirSupply(player.getMaxAirSupply());
                }
                SpellFx.impact(entity.serverLevel(), entity.definition(), entity.position(), new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 0.8F);
                // the globe's body falls out of the air it held - unless it is held at the caster's
                // arm's length, where ten drops a block from the eyes are a clump of blue squares
                if (!atArmsLength(entity)) {
                    float r = entity.radius();
                    entity.serverLevel().sendParticles(ParticleTypes.FALLING_WATER, entity.getX(), entity.getY() - r * 0.3D, entity.getZ(), 10, r * 0.45D, r * 0.35D, r * 0.45D, 0.0D);
                }
            }
        };
    }

    /**
     * Whether the globe has stayed put since its last tick. A bead hangs where it was set, so a
     * victim walking off with the globe (a player; a mob's navigation is stopped) would otherwise
     * leave a trail of them hanging in the air behind it.
     */
    private static boolean still(SpellEffectEntity entity) {
        return entity.distanceToSqr(entity.xo, entity.yo, entity.zo) < 0.0025D;
    }

    /** Whether the globe hangs within reach of its caster's eyes: a sneak-held bell, in their own view. */
    private static boolean atArmsLength(SpellEffectEntity entity) {
        Entity owner = entity.owner();
        return owner != null && entity.distanceToSqr(owner.getEyePosition()) < ARMS_LENGTH_SQR;
    }

    /**
     * The water slapping shut over the head: the one beat a direct seal has. Vanilla's splash is a
     * few pixels at the range the bell is cast from, so a ring of spray is thrown off the globe's
     * waist as it closes - soft, lit, spreading out and thinning - and the sheet the globe did not
     * keep runs off its underside.
     */
    private static void seal(ServerLevel level, Vec3 head, float radius, int bright) {
        level.sendParticles(ParticleTypes.SPLASH, head.x, head.y, head.z, SEAL_SPLASH, 0.3D, 0.25D, 0.3D, 0.0D);
        TintedParticleOptions spray = new TintedParticleOptions(MagicalParticles.WISP.get(), Palette.mix(bright, 0xFFFFFF, 0.5F), SEAL_SPRAY_SCALE);
        double offset = level.random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < SEAL_SPRAY; i++) {
            double a = offset + i * Math.PI * 2.0D / SEAL_SPRAY;
            double c = Math.cos(a);
            double s = Math.sin(a);
            level.sendParticles(spray, head.x + c * radius * 0.8D, head.y - radius * 0.2D, head.z + s * radius * 0.8D, 0, c, 0.15D, s, SEAL_SPRAY_SPEED);
        }
        level.sendParticles(ParticleTypes.FALLING_WATER, head.x, head.y - radius * 0.5D, head.z, SEAL_RUNOFF, radius * 0.5D, radius * 0.2D, radius * 0.5D, 0.0D);
    }

    /** One bead of water on the globe's lower skin: it hangs there a moment, then drips off. */
    private static void bead(ServerLevel level, Vec3 centre, float radius) {
        double angle = level.random.nextDouble() * Math.PI * 2.0D;
        double down = 0.3D + 0.65D * level.random.nextDouble();
        double ring = Math.sqrt(1.0D - down * down) * radius;
        level.sendParticles(ParticleTypes.DRIPPING_WATER, centre.x + Math.cos(angle) * ring, centre.y - down * radius, centre.z + Math.sin(angle) * ring, 0, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .circle(CircleScript.of(SchoolMaterial.WATER).emblem(EmblemId.BELL).frame(9).band(GlyphKind.CHAIN_BAND, 18).stamps(StampId.TEARDROP, 9).core(CoreKind.RIPPLE).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.field(Silhouette.Form.SPHERE, FxKinds.Field.RIPPLE_WATER, 0.55F, 0.55F, 4, 6).withOpacity(0.75F))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.HOLLOW_SHELL, 0.25F, 4, 8).withOffset(0.55F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.DROPLET, FxKinds.Overlay.TUNNEL)
                .bounds(2.0F, 2.0F, 2.0F);
    }
}
