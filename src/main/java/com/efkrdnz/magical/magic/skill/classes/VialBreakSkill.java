package com.efkrdnz.magical.magic.skill.classes;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.AimResolver;
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
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * VENOMANCER - LOB / ARCED_FLASK / LINGERING_POOL. A flask thrown on a real arc that breaks where it
 * lands and leaves a pool eating whatever stands in it. The pool stacks the INFECTED status, which
 * is exactly what the Venomancer passive wants on a target before the next spell arrives, so the
 * skill and Compounding Venom are built to be used together.
 */
public final class VialBreakSkill implements SkillModule {
    private static final int FLIGHT = 24;
    private static final double GRAVITY = 0.04D;
    private static final int POOL_TICK_INTERVAL = 10;
    private static final int INFECT_TICKS = 120;
    private static final float POOL_DAMAGE_SHARE = 0.5F;
    /**
     * Ticks between the pool's swirls, and how many rise each time. The pool is the skill, so it
     * breathes a little over the usual lingering rate (two and a half a tick): three a beat, piled
     * in the middle by a gaussian, read as a few specks at the target's feet rather than a pool.
     */
    private static final int LINGER_INTERVAL = 2;
    private static final int LINGER_SWIRLS = 5;
    /** How far out the swirls rise, as a share of the pool's reach: all over it, as a lingering potion's do. */
    private static final double LINGER_REACH = 0.85D;
    /** How far under the break the pool looks for a floor to lie on. */
    private static final int FLOOR_SEARCH = 4;
    /** The mode bit that switches a broken vial to draw subset 1: bit 0 stays the sneak bit. */
    private static final byte DRAW_POOL = 2;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.VIAL_BREAK;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 start = ctx.eye().add(ctx.look().scale(0.8D));
                Vec3 throwVelocity = ctx.look().scale(0.55D * Math.max(0.5D, ctx.stats().speed())).add(0.0D, 0.14D, 0.0D);
                SpellEffectEntity flask = SpellEffectEntity.spawn(ctx, start, FLIGHT + Math.max(60, ctx.duration()), ctx.size(), ctx.look());
                CompoundTag data = flask.serverData();
                data.putDouble("VX", throwVelocity.x);
                data.putDouble("VY", throwVelocity.y);
                data.putDouble("VZ", throwVelocity.z);
                data.putBoolean("Broken", false);
                // no release here: the casting service plays it on SUCCESS, and a second one doubled the
                // muzzle flash, the burst, the sound and the caster's recoil
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 24.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(4.0F, 18.0F);
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                CompoundTag data = entity.serverData();
                if (!data.getBoolean("Broken")) {
                    flyAndMaybeBreak(entity, level, data);
                    return;
                }
                entity.setValue(1.0F);
                linger(level, entity);
                if (entity.tickCount % POOL_TICK_INTERVAL != 0) {
                    return;
                }
                // The pool does the work: small bites, but it keeps the target loaded with statuses.
                for (LivingEntity victim : SkillTargets.hostilesWithin(level, entity.owner(), entity.position(), entity.radius())) {
                    SkillTargets.hurt(level, entity.owner(), victim, entity.damage() * POOL_DAMAGE_SHARE, entity.definition(), false);
                    MagicStatusService.apply(victim, MagicStatus.INFECTED, INFECT_TICKS, entity.definition().id(), entity.owner());
                    victim.addEffect(new MobEffectInstance(MobEffects.POISON, INFECT_TICKS, 0, true, true));
                }
            }

            private void flyAndMaybeBreak(SpellEffectEntity entity, ServerLevel level, CompoundTag data) {
                Vec3 velocity = new Vec3(data.getDouble("VX"), data.getDouble("VY") - GRAVITY, data.getDouble("VZ"));
                Vec3 next = entity.position().add(velocity);
                data.putDouble("VX", velocity.x);
                data.putDouble("VY", velocity.y);
                data.putDouble("VZ", velocity.z);
                entity.setPos(next);
                entity.setValue(0.0F);

                boolean hitGround = !level.getBlockState(net.minecraft.core.BlockPos.containing(next)).isAir();
                boolean hitBody = !SkillTargets.hostilesWithin(level, entity.owner(), next, 0.8D).isEmpty();
                if (entity.tickCount < FLIGHT && !hitGround && !hitBody) {
                    return;
                }
                data.putBoolean("Broken", true);
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                // draw the pool's subset from here on, so the vial's drop does not hang over its own puddle
                entity.setMode((byte) (entity.mode() | DRAW_POOL));
                SpellFx.impact(level, entity.definition(), next, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 1.2F);
            }
        };
    }

    /**
     * The pool drawn the way Minecraft draws a lingering potion: the venom's own green swirling up
     * off the ground it covers, and now and then a bubble breaking on it. It lies on the floor
     * under the break, so a vial burst on a body still pools at that body's feet.
     */
    private static void linger(ServerLevel level, SpellEffectEntity pool) {
        if (pool.tickCount % LINGER_INTERVAL != 0) {
            return;
        }
        Vec3 at = pool.position();
        Vec3 floor = AimResolver.groundBelow(level, at, FLOOR_SEARCH);
        double y = (floor != null ? floor.y : at.y) + 0.1D;
        double spread = pool.radius() * 0.45D;
        int venom = VisualProfiles.of(pool.definition()).color(ColorRole.BASE);
        ColorParticleOption swirl = ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | venom);
        // spread evenly over the disc, the way vanilla's own area cloud picks its points, so the
        // pool has an edge where the pool actually ends
        double reach = pool.radius() * LINGER_REACH;
        for (int i = 0; i < LINGER_SWIRLS; i++) {
            double r = reach * Math.sqrt(level.random.nextDouble());
            double a = level.random.nextDouble() * Math.PI * 2.0D;
            level.sendParticles(swirl, at.x + Math.cos(a) * r, y, at.z + Math.sin(a) * r, 0, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        if (pool.tickCount % (LINGER_INTERVAL * 3) == 0) {
            level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, y, at.z, 2, spread * 0.7D, 0.02D, spread * 0.7D, 0.0D);
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .palette(3)
                // a thrown vial of venom: potion swirls in flight and in the pool, glass where it breaks
                .accent(Accent.BREW)
                .circle(CircleScript.of(SchoolMaterial.WATER).emblem(EmblemId.SKULL).frame(13).band(GlyphKind.WAVE_BAND, 22).band(GlyphKind.FACET_BAND, 11).stamps(StampId.DROP, 11).core(CoreKind.DISC_GLOW).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                // the vial in flight only; once it breaks the entity is the pool and the drop would sit on it
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.LIQUID_DROP, 0.3F, 1, 2).forModes(0))
                // No shader pool. The field was drawn in every mode, so it hung round the vial for its
                // whole flight; the pool is the lingering swirl behaviour() lays on the floor instead.
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.OVERGROWTH, FxKinds.Smoke.SPORE_DOTS, FxKinds.Overlay.INK_BLEED)
                .bounds(3.0F, 2.0F, 1.0F);
    }
}
