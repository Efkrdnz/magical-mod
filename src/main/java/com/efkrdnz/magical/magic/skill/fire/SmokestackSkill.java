package com.efkrdnz.magical.magic.skill.fire;

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
import com.efkrdnz.magical.magic.service.TargetDenialService;
import com.efkrdnz.magical.magic.skill.SkillModule;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * FIRE T0 - OBSCURE / AIM_POINT / SMOKE_COLUMN. A dense column of black smoke: mobs cannot acquire
 * targets inside it, from inside it, or through it; anything inside is slowed and lightly burned.
 * Sneak = plant it at your own feet.
 */
public final class SmokestackSkill implements SkillModule {
    /**
     * Dark smoke puffs raised every tick through the stack. The column is the whole skill - it is
     * there to block sight - so it runs well over the usual lingering budget on purpose: at a puff
     * and a half a tick a six-block-wide stack read as a few specks, because a large-smoke sprite
     * shrinks to a dot over its life and most of it is spent small.
     */
    private static final int SMOKE_PUFFS = 3;
    /** The stack is thrown up at once on its first tick, so it is standing by the time anyone looks. */
    private static final int OPENING_PUFFS = 20;
    /**
     * Big campfire billows: the body that actually hides what is behind it, and the plume that rises
     * out of the top. One every few ticks, each drifting straight up at a campfire's own pace so it
     * clears the five-block stack before it fades.
     */
    private static final int BILLOW_INTERVAL = 3;
    private static final int OPENING_BILLOWS = 6;
    private static final double BILLOW_RISE = 0.04D;
    private static final double BILLOW_RISE_SPREAD = 0.025D;
    /**
     * The puffs rise from a ring rather than the middle, a third of the radius clear: from outside
     * the ring reads as a full column, and a caster who planted it at their feet stands in a wall of
     * smoke instead of inside a puff.
     */
    private static final double SMOKE_CORE = 0.35D;
    /** A small flame at the foot of the stack every this many ticks: the fire under the smoke. */
    private static final int EMBER_INTERVAL = 6;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SMOKESTACK;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 pos = ctx.sneak() ? ctx.feet() : ctx.aim().point();
                SpellEffectEntity column = SpellEffectEntity.spawn(ctx, pos, Math.max(40, ctx.duration()), Math.max(1.5F, ctx.size()), new Vec3(0.0D, 1.0D, 0.0D));
                column.setValue(5.0F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 14.0D;
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
        return entity -> {
            double height = entity.value() > 0.0F ? entity.value() : 5.0D;
            TargetDenialService.register(entity.level(), entity.position(), entity.radius(), height);
            billow(entity, height);
            if (entity.tickCount % 4 != 0) {
                return;
            }
            for (LivingEntity inside : SkillTargets.hostilesInCylinder(entity.serverLevel(), entity.owner(), entity.position(), entity.radius(), height)) {
                inside.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 0, false, false));
                if (entity.tickCount % 20 == 0) {
                    SkillTargets.hurt(entity.serverLevel(), entity.owner(), inside, entity.damage(), entity.definition().id());
                }
                if (inside instanceof ServerPlayer player && entity.tickCount % 8 == 0) {
                    SpellFx.overlay(player, entity.definition(), FxKinds.Overlay.INK_BLEED, 12, 0.55F, ColorRole.INK);
                }
            }
        };
    }

    /**
     * The column's body, in three layers: dark large smoke rolling up through the whole stack, big
     * campfire billows that give it bulk and carry it on up past the top as a plume, and the shader
     * veil (the profile's swarm) filling the gaps between them dark. Forty-eight veil puffs alone
     * stacked into one black cellular blot; a puff and a half of large smoke alone was a few specks.
     */
    private static void billow(SpellEffectEntity entity, double height) {
        ServerLevel level = entity.serverLevel();
        RandomSource random = level.random;
        Vec3 base = entity.position();
        double radius = entity.radius();
        boolean opening = entity.tickCount == 1;
        int puffs = opening ? OPENING_PUFFS : SMOKE_PUFFS;
        for (int i = 0; i < puffs; i++) {
            Vec3 p = inStack(base, radius, random, 0.2D + random.nextDouble() * height * 0.7D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, 1, 0.15D, 0.3D, 0.15D, 0.01D);
        }
        int billows = opening ? OPENING_BILLOWS : entity.tickCount % BILLOW_INTERVAL == 0 ? 1 : 0;
        for (int i = 0; i < billows; i++) {
            Vec3 p = inStack(base, radius, random, 0.3D + random.nextDouble() * height * 0.5D);
            double rise = BILLOW_RISE + random.nextDouble() * BILLOW_RISE_SPREAD;
            // count 0: one billow, sent at exactly this velocity; a campfire puff keeps it all its life
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y, p.z, 0,
                    (random.nextDouble() - 0.5D) * 0.01D, rise, (random.nextDouble() - 0.5D) * 0.01D, 1.0D);
        }
        if (entity.tickCount % EMBER_INTERVAL == 0) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double r = radius * (SMOKE_CORE + (1.0D - SMOKE_CORE) * random.nextDouble());
            level.sendParticles(ParticleTypes.SMALL_FLAME, base.x + Math.cos(a) * r, base.y + 0.1D, base.z + Math.sin(a) * r, 1, 0.1D, 0.02D, 0.1D, 0.005D);
        }
    }

    /** A point in the stack's ring at this height above its foot, spread evenly over the ring's area. */
    private static Vec3 inStack(Vec3 base, double radius, RandomSource random, double y) {
        double a = random.nextDouble() * Math.PI * 2.0D;
        double r = radius * (SMOKE_CORE + (1.0D - SMOKE_CORE) * Math.sqrt(random.nextDouble()));
        return new Vec3(base.x + Math.cos(a) * r, base.y + y, base.z + Math.sin(a) * r);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.FIRE)
                .palette(3)
                .circle(CircleScript.of(SchoolMaterial.FIRE).emblem(EmblemId.CHIMNEY).frame(3).band(GlyphKind.TOOTH_BAND, 12).stamps(StampId.DOT, 6).core(CoreKind.EMBER_PIT).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                // the dark veil between the real smoke the behaviour raises: sixteen at half opacity
                // left only wisps and the column lost its black, forty-eight at full was a flat blot.
                // The ember orb that sat at its foot drew as a dark disc of dots; small flames there
                // now do its job
                .silhouette(Silhouette.swarm(Silhouette.Form.COLUMN, FxKinds.Smoke.SMOKE_PUFF, 30, 3.0F, 5.0F).withOpacity(0.75F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SCORCH_DECAL, FxKinds.Smoke.SMOKE_PUFF, FxKinds.Overlay.INK_BLEED)
                .budget(1)
                .bounds(4.0F, 6.0F, 1.0F);
    }
}
