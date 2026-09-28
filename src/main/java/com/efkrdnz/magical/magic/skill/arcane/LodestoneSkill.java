package com.efkrdnz.magical.magic.skill.arcane;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.SpellIntercept;
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
import com.efkrdnz.magical.magic.visual.VisualProfiles;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * ARCANE T0 - ATTRACT_PROJECTILES / AIM_POINT_PLANT / RADIAL_WELL. A hovering magnetite needle
 * bends every hostile projectile within 6 blocks toward itself and swallows what reaches it (six
 * charges). Sneak = reverse polarity: bends them away.
 */
public final class LodestoneSkill implements SkillModule {
    private static final int CHARGES = 6;

    /** The magnetite needle's height; its middle is where the field runs in to. */
    private static final float NEEDLE_HEIGHT = 1.3F;

    /** Ticks between beats of the field drawn in matter. */
    private static final int FIELD_INTERVAL = 3;

    /** Glyphs drawn in on each beat of the field. */
    private static final int FIELD_GLYPHS = 2;

    /** Grit of the needle knocked loose by each projectile it swallows. */
    private static final int SWALLOW_GRIT = 8;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.LODESTONE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 pos = ctx.aim().point().add(ctx.aim().normal().scale(0.9D));
                SpellEffectEntity stone = SpellEffectEntity.spawn(ctx, pos, Math.max(40, ctx.duration()), 6.0F * Math.max(0.5F, ctx.size()), ctx.aim().normal());
                stone.setExtra(CHARGES);
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
            boolean repel = entity.sneakMode();
            double radius = entity.radius();
            Vec3 centre = entity.position();
            for (Entity projectile : SpellIntercept.hostileProjectiles(entity.serverLevel(), centre, radius, entity.owner())) {
                double dist = projectile.position().distanceTo(centre);
                if (!repel && dist < 0.6D) {
                    SpellIntercept.erase(projectile);
                    entity.setExtra(entity.extra() - 1);
                    SpellFx.impact(entity.serverLevel(), entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 0.6F);
                    swallowGrit(entity.serverLevel(), centre);
                    if (entity.extra() <= 0) {
                        entity.finish();
                        return;
                    }
                    continue;
                }
                float maxDeg = (float) (22.0D + 26.0D * (1.0D - Math.min(1.0D, dist / radius)));
                Vec3 target = repel ? projectile.position().add(projectile.position().subtract(centre).normalize().scale(4.0D)) : centre;
                SpellIntercept.bendToward(projectile, target, maxDeg);
            }
            entity.setValue(entity.extra() / (float) CHARGES);
            if (entity.tickCount % FIELD_INTERVAL == 0) {
                fieldLines(entity, centre, repel);
            }
        };
    }

    /**
     * The needle's field, in matter: vanilla's enchanting glyphs arc in from a few blocks out and
     * drop into its middle, the way they fall into an enchanting table's book - or, reversed, a
     * rune is thrown off it. A couple a beat, so it reads as a steady pull and never as a cloud.
     */
    private static void fieldLines(SpellEffectEntity entity, Vec3 base, boolean repel) {
        ServerLevel level = entity.serverLevel();
        RandomSource random = level.random;
        Vec3 middle = base.add(0.0D, NEEDLE_HEIGHT * 0.5D, 0.0D);
        if (repel) {
            TintedParticleOptions rune = new TintedParticleOptions(MagicalParticles.RUNE.get(), VisualProfiles.of(entity.definition()).color(ColorRole.BRIGHT), 1.0F);
            double a = random.nextDouble() * Math.PI * 2.0D;
            Vec3 out = new Vec3(Math.cos(a), random.nextDouble() * 0.6D - 0.2D, Math.sin(a)).normalize();
            level.sendParticles(rune, middle.x + out.x * 0.3D, middle.y + out.y * 0.3D, middle.z + out.z * 0.3D, 0, out.x, out.y, out.z, 0.14D);
            return;
        }
        for (int i = 0; i < FIELD_GLYPHS; i++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double r = 2.0D + random.nextDouble() * 1.5D;
            Vec3 from = new Vec3(Math.cos(a) * r, random.nextDouble() * 1.6D - 0.6D, Math.sin(a) * r);
            // the glyph starts at its position plus its speed and flies back to its position,
            // dropping 1.2 blocks at the very end - so it is aimed 1.2 over the middle
            level.sendParticles(ParticleTypes.ENCHANT, middle.x, middle.y + 1.2D, middle.z, 0, from.x, from.y - 1.2D, from.z, 1.0D);
        }
    }

    /** Grit of lodestone knocked off the needle as it swallows a shot: the cue carries the light. */
    private static void swallowGrit(ServerLevel level, Vec3 at) {
        BlockParticleOption grit = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.LODESTONE.defaultBlockState());
        level.sendParticles(grit, at.x, at.y + NEEDLE_HEIGHT * 0.5D, at.z, SWALLOW_GRIT, 0.1D, NEEDLE_HEIGHT * 0.25D, 0.1D, 0.12D);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ARCANE)
                .circle(CircleScript.of(SchoolMaterial.ARCANE).emblem(EmblemId.HORSESHOE).frame(8).band(GlyphKind.DASHED_RING, 16).stamps(StampId.BAR, 8).core(CoreKind.HEX_LENS).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.body(Silhouette.Form.PRISM, FxKinds.Body.STONE, 6, 0.16F, NEEDLE_HEIGHT))
                .silhouette(Silhouette.filament(Silhouette.Form.RING, FxKinds.Filament.RUNE_THREAD, 6, 0.06F, 1.6F, 0))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.HEX_CELLS, FxKinds.Smoke.LENS_SPARKLE, FxKinds.Overlay.HEX_PULSE)
                .bounds(4.0F, 3.0F, 2.0F);
    }
}
