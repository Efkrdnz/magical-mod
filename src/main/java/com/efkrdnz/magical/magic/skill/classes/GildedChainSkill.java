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
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * DIVINESMITH - LINK_PAIR / AIM_POINT / RIGID_BAR_BETWEEN_TWO_BODIES. The two hostiles nearest the
 * aim point are riveted together by a chain of light: neither can stray more than the leash from
 * the other (taut = both yanked together and bitten), and the chain itself is a hot bar that burns
 * anything else crossing it. Missing ends are stakes. Sneak = a third stake at the aim point.
 */
public final class GildedChainSkill implements SkillModule {
    private static final double LEASH = 4.0D;
    private static final double BAR_RADIUS = 0.5D;
    /** How often a taut chain drops molten metal, in ticks. */
    private static final int DRIP_INTERVAL = 3;
    /**
     * A taut chain bites both bodies every half second, and a full hit cue on every bite stood
     * about thirty end-rod glints on each of them (a glint lives three seconds) under a ring of
     * puffs twice a second. So the full cue goes with the first bite and every third after it; the
     * bites between throw a few gold motes off the body, which twinkle out inside a second.
     */
    private static final int FULL_BITE_EVERY = 3;
    private static final int BITE_MOTES = 4;
    private static final float BITE_MOTE_SCALE = 1.4F;
    /** Server-only: how many bites the chain has taken, for which of them carries the full cue. */
    private static final String BITE_COUNT_KEY = "BiteFx";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.GILDED_CHAIN;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                Vec3 point = ctx.aim().point();
                List<LivingEntity> near = SkillTargets.hostilesWithin(ctx.level(), ctx.caster(), point, 6.0D);
                LivingEntity a = near.size() > 0 ? near.get(0) : null;
                LivingEntity b = near.size() > 1 ? near.get(1) : null;
                if (a == null && b == null) {
                    SpellFx.decal(ctx.level(), ctx.definition(), point, ctx.aim().normal(), 1.0F);
                    return CastResult.CONSUMED_NO_COOLDOWN;
                }
                SpellEffectEntity chain = SpellEffectEntity.spawn(ctx, point.add(0.0D, 1.0D, 0.0D), Math.max(40, ctx.duration()), (float) LEASH, new Vec3(0.0D, 1.0D, 0.0D));
                CompoundTag data = chain.serverData();
                if (a != null) {
                    data.putUUID("A", a.getUUID());
                    SkillTargets.hurt(ctx.level(), ctx.caster(), a, ctx.damage(), ctx.definition(), true);
                }
                if (b != null) {
                    data.putUUID("B", b.getUUID());
                    SkillTargets.hurt(ctx.level(), ctx.caster(), b, ctx.damage(), ctx.definition(), true);
                }
                data.putDouble("SX", point.x);
                data.putDouble("SY", point.y);
                data.putDouble("SZ", point.z);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 24.0D;
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
                return MobCastProfile.control(4.0F, 20.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    private static LivingEntity end(ServerLevel level, CompoundTag data, String key) {
        return data.hasUUID(key) && level.getEntity(data.getUUID(key)) instanceof LivingEntity living && living.isAlive() ? living : null;
    }

    /**
     * A chain drawn taut goes white-hot and sheds molten metal: one drop off a link somewhere along
     * it, falling to the floor. It is the bar's heat made visible - the thing that burns whatever
     * crosses it - where the painter can only brighten the links.
     */
    private static void drip(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 at = from.add(to.subtract(from).scale(level.random.nextDouble()));
        level.sendParticles(ParticleTypes.FALLING_LAVA, at.x, at.y - 0.15D, at.z, 1, 0.05D, 0.0D, 0.05D, 0.0D);
    }

    /** A light bite: a few gold motes off the bitten body in place of the full hit cue. */
    private static void glint(ServerLevel level, LivingEntity victim, MagicSkillDefinition definition) {
        Vec3 c = victim.getBoundingBox().getCenter();
        double across = victim.getBbWidth() * 0.3D;
        TintedParticleOptions mote = new TintedParticleOptions(MagicalParticles.MOTE.get(), VisualProfiles.of(definition).color(ColorRole.BRIGHT), BITE_MOTE_SCALE);
        level.sendParticles(mote, c.x, c.y, c.z, BITE_MOTES, across, victim.getBbHeight() * 0.25D, across, 0.02D);
    }

    private static void pull(LivingEntity e, Vec3 toward, double strength) {
        Vec3 d = toward.subtract(e.position());
        d = new Vec3(d.x, 0.0D, d.z);
        if (d.lengthSqr() < 1.0E-4D) {
            return;
        }
        d = d.normalize().scale(strength);
        e.setDeltaMovement(d.x, Math.max(e.getDeltaMovement().y, 0.1D), d.z);
        e.hurtMarked = true;
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            Entity owner = entity.owner();
            CompoundTag data = entity.serverData();
            Vec3 stake = new Vec3(data.getDouble("SX"), data.getDouble("SY"), data.getDouble("SZ"));
            LivingEntity a = end(level, data, "A");
            LivingEntity b = end(level, data, "B");
            if (a == null && b == null) {
                entity.finish();
                return;
            }
            Vec3 pa = a != null ? a.position() : stake;
            Vec3 pb = b != null ? b.position() : stake;
            boolean taut = false;
            if (pa.distanceTo(pb) > LEASH) {
                taut = true;
                Vec3 mid = pa.add(pb).scale(0.5D);
                if (a != null) {
                    pull(a, b != null ? mid : pb, 0.4D);
                }
                if (b != null) {
                    pull(b, a != null ? mid : pa, 0.4D);
                }
                if (data.getInt("BiteIcd") <= entity.tickCount) {
                    data.putInt("BiteIcd", entity.tickCount + 10);
                    int bite = data.getInt(BITE_COUNT_KEY);
                    data.putInt(BITE_COUNT_KEY, bite + 1);
                    boolean fullCue = bite % FULL_BITE_EVERY == 0;
                    if (a != null) {
                        SkillTargets.hurt(level, owner, a, 2.0F, entity.definition(), fullCue);
                        if (!fullCue) {
                            glint(level, a, entity.definition());
                        }
                    }
                    if (b != null) {
                        SkillTargets.hurt(level, owner, b, 2.0F, entity.definition(), fullCue);
                        if (!fullCue) {
                            glint(level, b, entity.definition());
                        }
                    }
                }
            }
            if (entity.sneakMode()) {
                for (LivingEntity e : new LivingEntity[] {a, b}) {
                    if (e != null && e.position().distanceTo(stake) > LEASH) {
                        pull(e, stake, 0.35D);
                        taut = true;
                    }
                }
            }
            // the hot bar: anything else crossing the capsule between the ends
            Vec3 ca = pa.add(0.0D, 1.0D, 0.0D);
            Vec3 cb = pb.add(0.0D, 1.0D, 0.0D);
            AABB box = new AABB(ca, cb).inflate(BAR_RADIUS + 0.5D);
            for (LivingEntity other : SkillTargets.hostilesIn(level, owner, box)) {
                if (other == a || other == b) {
                    continue;
                }
                Vec3 p = other.getBoundingBox().getCenter();
                Vec3 ab = cb.subtract(ca);
                double len2 = ab.lengthSqr();
                double t = len2 < 1.0E-6D ? 0.0D : Math.max(0.0D, Math.min(1.0D, p.subtract(ca).dot(ab) / len2));
                if (p.distanceTo(ca.add(ab.scale(t))) > BAR_RADIUS + other.getBbWidth() * 0.5D) {
                    continue;
                }
                String key = "bar_" + other.getId();
                if (data.getInt(key) > entity.tickCount) {
                    continue;
                }
                data.putInt(key, entity.tickCount + 20);
                SkillTargets.hurt(level, owner, other, entity.damage(), entity.definition(), true);
                other.igniteForSeconds(3.0F);
            }
            if (taut && entity.tickCount % DRIP_INTERVAL == 0) {
                drip(level, ca, cb);
            }
            entity.setPos(ca.add(cb).scale(0.5D));
            CompoundTag synced = new CompoundTag();
            synced.putDouble("AX", ca.x);
            synced.putDouble("AY", ca.y);
            synced.putDouble("AZ", ca.z);
            synced.putDouble("BX", cb.x);
            synced.putDouble("BY", cb.y);
            synced.putDouble("BZ", cb.z);
            synced.putBoolean("Taut", taut);
            if (entity.sneakMode()) {
                synced.putDouble("SX", stake.x);
                synced.putDouble("SY", stake.y + 1.0D);
                synced.putDouble("SZ", stake.z);
            }
            entity.setSyncedData(synced);
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.LIGHT)
                .palette(1)
                // A chain of light: a bite throws gold motes and glints, and the taut chain sheds its
                // own molten drops (drip). The forge accent it wore made every bite at this tier a
                // heavy hit - an explosion sprite, a ring of seven puffs, black smoke and slag on
                // both chained bodies twice a second for as long as the chain was drawn taut - and
                // its release put a clump of that smoke over the caster's crosshair.
                .accent(Accent.RADIANT)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.CHAIN).frame(10).band(GlyphKind.CHAIN_BAND, 14).band(GlyphKind.TICK_BAND, 30).stamps(StampId.LINK, 10).orbit(5, 0.84F, 4).core(CoreKind.SUNBURST).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.custom("gilded_chain", 4.0F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.SPARK_STREAK, FxKinds.Overlay.HEX_PULSE)
                .bounds(8.0F, 3.0F, 3.0F);
    }
}
