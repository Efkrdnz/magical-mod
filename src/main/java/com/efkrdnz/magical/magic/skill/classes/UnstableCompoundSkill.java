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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * TRANSMUTER - PLANT_BOMB / AIM_TARGET_HITSCAN / CARRIED_POINT_BURST. After a windup a splash of
 * reagent plants on the first living creature along the look (else the struck face, else the ray
 * end) and rides it for a fixed fuse, then detonates wherever the carrier is; nothing hurries or
 * delays it. Sneak = stick it to the surface under the aim point as a timed mine.
 */
public final class UnstableCompoundSkill implements SkillModule {
    private static final int WINDUP = 10;
    private static final byte MODE_EGG = 2;
    private static final double RANGE = 12.0D;
    /** Ticks between the planted compound's swirls, and between them once the fuse is nearly out. */
    private static final int FIZZ_INTERVAL = 5;
    private static final int FIZZ_LATE_INTERVAL = 2;
    /** How much of the fuse has to have burnt before it fizzes hard and smokes. */
    private static final float LATE_FUSE = 0.66F;
    /**
     * Within five blocks of its thrower (squared) the blast sprite is drawn at vanilla's size 1,
     * two blocks across, instead of 0, four across.
     */
    private static final double CLOSE_BLAST_SQR = 25.0D;
    private static final double CLOSE_BLAST_SIZE = 1.0D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.UNSTABLE_COMPOUND;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                SpellEffectEntity hand = SpellEffectEntity.spawn(ctx, ctx.eye().add(ctx.look().scale(0.8D)), WINDUP, 0.35F, ctx.look());
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return RANGE;
            }

            @Override
            public double aimTolerance() {
                return 0.8D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(3.0F, 12.0F);
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
                if ((entity.mode() & MODE_EGG) != 0) {
                    Entity carrier = entity.target();
                    if (carrier != null && carrier.isAlive()) {
                        entity.setPos(carrier.getX(), carrier.getY() + carrier.getBbHeight() * 0.6D, carrier.getZ());
                    }
                    entity.setValue(entity.tickCount / (float) Math.max(1, entity.life()));
                    fizz(level, entity);
                    return;
                }
                LivingEntity owner = entity.livingOwner();
                if (owner == null || !owner.isAlive()) {
                    entity.finish();
                    return;
                }
                entity.setPos(owner.getEyePosition().add(owner.getLookAngle().scale(0.8D)).add(0.0D, -0.2D, 0.0D));
                if (entity.tickCount != WINDUP - 1) {
                    return;
                }
                boolean mine = entity.sneakMode();
                AimResolver.Result aim = AimResolver.resolve(level, owner, owner.getLookAngle(), RANGE, mine ? 0.0D : 0.8D, mine, 8, e -> SkillTargets.isHostile(owner, e));
                int fuse = Math.max(20, entity.duration());
                SpellEffectEntity egg = SpellEffectEntity.create(level, entity.definition(), null, owner, aim.point(), fuse, 3.5F, aim.normal(), entity.seed());
                egg.setMode((byte) ((mine ? 1 : 0) | MODE_EGG));
                egg.copyStatsFrom(entity);
                LivingEntity carrier = mine ? null : aim.living();
                if (carrier != null && SkillTargets.isHostile(owner, carrier)) {
                    egg.setTarget(carrier);
                    egg.setPos(carrier.getX(), carrier.getY() + carrier.getBbHeight() * 0.6D, carrier.getZ());
                }
                level.addFreshEntity(egg);
                SpellFx.impact(level, entity.definition(), egg.position(), aim.normal(), carrier, owner, 0.7F);
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                if ((entity.mode() & MODE_EGG) == 0) {
                    return;
                }
                ServerLevel level = entity.serverLevel();
                Entity owner = entity.owner();
                float damage = entity.damage();
                float knockback = entity.knockback();
                Vec3 centre = entity.position();
                for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, entity.getBoundingBox().inflate(entity.radius()), e -> e.isAlive() && e != owner && !SkillTargets.isAlly(owner, e))) {
                    if (victim.getBoundingBox().getCenter().distanceTo(centre) > entity.radius()) {
                        continue;
                    }
                    SkillTargets.hurt(level, owner, victim, damage, entity.definition(), true);
                    SkillTargets.shove(victim, centre, 0.6D * Math.max(0.5D, knockback), 0.25D);
                }
                SpellFx.impact(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), null, owner, 2.0F);
                // The brew accent reads a heavy hit as a spill, not a blast, which is right for a
                // flask and wrong for this: the compound goes off. One vanilla explosion at its heart,
                // at its default size (four blocks across, about the blast's own reach), under the
                // reagent the accent throws. The compound rides a hostile, and a hostile is often in
                // arm's reach when it goes: then the sprite is a creeper's puff, half as wide, so it
                // does not stand across the thrower's whole view. Its x speed is read as its size.
                double puff = owner != null && owner.distanceToSqr(centre) < CLOSE_BLAST_SQR ? CLOSE_BLAST_SIZE : 0.0D;
                level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y, centre.z, 0, puff, 0.0D, 0.0D, 1.0D);
                SpellFx.decal(level, entity.definition(), centre, new Vec3(0.0D, 1.0D, 0.0D), 2.0F);
            }
        };
    }

    /**
     * The compound at work on whatever carries it: its own gold swirling off it like a potion's,
     * slowly at first and quickening for the last third of the fuse, when smoke joins in - so the
     * carrier and everyone near them can read that it is about to go without a clock on screen.
     */
    private static void fizz(ServerLevel level, SpellEffectEntity egg) {
        boolean late = egg.value() >= LATE_FUSE;
        if (egg.tickCount % (late ? FIZZ_LATE_INTERVAL : FIZZ_INTERVAL) != 0) {
            return;
        }
        Vec3 at = egg.position();
        int gold = VisualProfiles.of(egg.definition()).color(ColorRole.BASE);
        level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | gold),
                at.x, at.y, at.z, 2, 0.25D, 0.25D, 0.25D, 0.0D);
        if (late) {
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.2D, at.z, 1, 0.15D, 0.1D, 0.15D, 0.01D);
        }
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.LIGHT)
                .palette(2)
                // a splash of reagent: its matter is a potion's swirl, bubbles and vial glass
                .accent(Accent.BREW)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.ALEMBIC).frame(7).band(GlyphKind.TICK_BAND, 60).band(GlyphKind.CHAIN_BAND, 14).stamps(StampId.HOURGLASS, 7).orbit(3, 0.84F, 3).core(CoreKind.DISC_GLOW).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.orb(Silhouette.Form.STACK, FxKinds.Orb.CHARGE_SPHERE, 0.9F, 3, 10).withSize(0.9F, 0.5F).forModes(1))
                // held under the eyes for the windup: at 0.3 it was a sun across a third of the view
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.LIQUID_DROP, 0.16F, 2, 4).withOpacity(0.8F).forModes(0))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SCORCH_DECAL, FxKinds.Smoke.SPARK_STREAK, FxKinds.Overlay.FLASH)
                .bounds(4.0F, 2.0F, 2.0F);
    }
}
