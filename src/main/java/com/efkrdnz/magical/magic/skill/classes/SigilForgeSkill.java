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
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * RUNEWRIGHT - INSCRIBE / AIMED_SURFACE / PROXIMITY_TRIGGER. A rune cut into whatever you are
 * looking at and left there. It sits inert for a long time and goes off the moment something
 * hostile crosses it, rooting whatever it catches. A Runewright does not throw the spell; they
 * leave it lying around and let the fight walk into it. Sneak = a wider, shorter-lived sigil.
 */
public final class SigilForgeSkill implements SkillModule {
    /** Ticks of settling before the rune can fire, so it cannot detonate in the caster's face. */
    private static final int ARM = 20;
    private static final double TRIGGER = 2.4D;
    private static final double WIDE_TRIGGER = 4.0D;
    private static final int ROOT_TICKS = 40;
    /** Glyphs in the ring that lifts off the trigger's edge the moment the rune arms. */
    private static final int SET_GLYPHS = 10;
    /** Ticks between the single glyphs an armed rune gives off while it waits. */
    private static final int IDLE_GLYPH_TICKS = 20;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SIGIL_FORGE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                // Sneaking trades reach for patience: a wider rune that will not wait as long.
                int life = ctx.sneak() ? Math.max(80, ctx.duration() / 2) : Math.max(120, ctx.duration());
                SpellEffectEntity sigil = SpellEffectEntity.spawn(ctx, ctx.aim().point(), life, ctx.size(), new Vec3(0.0D, 1.0D, 0.0D));
                sigil.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                // the casting service releases every successful cast; a second release here stacked
                // two bloom flashes on the caster's hand on the same tick
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
                if (entity.tickCount < ARM) {
                    entity.setValue(entity.tickCount / (float) ARM);
                    return;
                }
                entity.setValue(1.0F);
                ServerLevel level = entity.serverLevel();
                double trigger = entity.sneakMode() ? WIDE_TRIGGER : TRIGGER;
                int armed = entity.tickCount - ARM;
                if (armed == 0) {
                    setRing(level, entity, trigger);
                } else if (armed % IDLE_GLYPH_TICKS == 0) {
                    idleGlyph(level, entity);
                }
                List<LivingEntity> crossing = SkillTargets.hostilesWithin(level, entity.owner(), entity.position(), trigger);
                if (!crossing.isEmpty()) {
                    entity.finish();
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                ServerLevel level = entity.serverLevel();
                double trigger = entity.sneakMode() ? WIDE_TRIGGER : TRIGGER;
                Vec3 at = entity.position();
                for (LivingEntity victim : SkillTargets.hostilesWithin(level, entity.owner(), at, trigger + entity.radius())) {
                    SkillTargets.hurt(level, entity.owner(), victim, entity.damage(), entity.definition(), true);
                    MagicStatusService.apply(victim, MagicStatus.ROOTED, ROOT_TICKS, entity.definition().id(), entity.owner());
                }
                SpellFx.impact(level, entity.definition(), at, new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 1.3F);
            }
        };
    }

    /**
     * The moment the rune sets: a ring of glyphs lifts off the ground where it will go off, at the
     * distance it goes off at. The cut sigil is small and faint by design, so this is the one time
     * the trap says out loud how far it reaches.
     */
    private static void setRing(ServerLevel level, SpellEffectEntity sigil, double trigger) {
        TintedParticleOptions rune = rune(sigil);
        Vec3 at = sigil.position();
        for (int i = 0; i < SET_GLYPHS; i++) {
            double a = i * Math.PI * 2.0D / SET_GLYPHS;
            // a rune keeps nine tenths of its speed a tick: it lifts about half a block and fades
            level.sendParticles(rune, at.x + Math.cos(a) * trigger, at.y + 0.05D, at.z + Math.sin(a) * trigger, 0, 0.0D, 0.05D, 0.0D, 1.0D);
        }
    }

    /**
     * A single glyph lifting off an armed rune now and then, so one left lying for twenty seconds
     * still reads as live rather than as a scuff on the floor: one glyph a second, never a cloud.
     */
    private static void idleGlyph(ServerLevel level, SpellEffectEntity sigil) {
        RandomSource random = level.random;
        double a = random.nextDouble() * Math.PI * 2.0D;
        double r = sigil.radius() * 0.6D * Math.sqrt(random.nextDouble());
        Vec3 at = sigil.position();
        level.sendParticles(rune(sigil), at.x + Math.cos(a) * r, at.y + 0.05D, at.z + Math.sin(a) * r, 0, 0.0D, 0.04D, 0.0D, 1.0D);
    }

    private static TintedParticleOptions rune(SpellEffectEntity sigil) {
        return new TintedParticleOptions(MagicalParticles.RUNE.get(), VisualProfiles.of(sigil.definition()).color(ColorRole.BRIGHT), 1.1F);
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SPATIAL)
                .palette(2)
                // a Runewright's cut rune: its matter is lifting glyphs, not a fold's crackle and glass
                .accent(Accent.RUNE)
                .circle(CircleScript.of(SchoolMaterial.SPATIAL).emblem(EmblemId.KNOT).frame(5).band(GlyphKind.RUNE_BAND, 18).band(GlyphKind.FACET_BAND, 6).stamps(StampId.KEY, 6).core(CoreKind.HEX_LENS).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.field(Silhouette.Form.GROUND_SEAM, FxKinds.Field.RUNE_PAPER, 1.5F, 0.1F))
                .silhouette(Silhouette.mark(FxKinds.Mark.LATTICE_GRID, 1.2F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.SIGIL_SLAM_FLASH, FxKinds.Smoke.RUNE_MOTE, FxKinds.Overlay.HEX_PULSE)
                .bounds(3.0F, 1.0F, 1.2F);
    }
}
