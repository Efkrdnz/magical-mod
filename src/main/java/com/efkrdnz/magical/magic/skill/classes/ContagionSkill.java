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
import com.efkrdnz.magical.magic.status.MagicStatusData;
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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * PLAGUEDOCTOR - SPREAD_BY_CONTACT / AIM_TARGET_HITSCAN / PROPAGATING_CARRIER_NETWORK. A lancet
 * infects the first hostile crossed (or leaves a stain that infects the first to touch it). The
 * outbreak beats on one shared clock: every carrier rots a little, and young carriers copy the
 * plague to whoever stands next to them. Sneak = seed it on yourself as a harmless carrier.
 */
public final class ContagionSkill implements SkillModule {
    private static final int BEAT = 20;
    private static final int MAX_GENERATION = 3;
    private static final double CONTACT = 1.5D;
    private static final double SCAN = 32.0D;
    private static final int STAIN_LIFE = 100;
    /** The plague's own smoke: a breath off each carrier on the beat, a thread onto each body it catches. */
    private static final int CARRIER_WISPS = 3;
    private static final float CARRIER_WISP_SCALE = 2.4F;
    private static final int JUMP_WISPS = 4;
    private static final float JUMP_WISP_SCALE = 1.6F;
    /** An armed stain seeps a little on this cadence until something touches it. */
    private static final int STAIN_SEEP_INTERVAL = 10;
    private static final int STAIN_WISPS = 2;
    private static final float STAIN_WISP_SCALE = 2.0F;
    /**
     * Between beats a carrier shows what it carries the way vanilla shows a poison: swirls in the
     * plague's colour rising off the body, a couple every quarter second. The beat's breath alone
     * left a carrier looking healthy for nineteen ticks in twenty.
     */
    private static final int SICK_INTERVAL = 5;
    private static final int SICK_SWIRLS = 2;
    /** Every other sick tick the body also seeps one wisp of the plague's smoke. */
    private static final int SEEP_INTERVAL = 10;
    /** The lancet going in: a cloud of the plague's swirls off the body it has just infected. */
    private static final int BLOOM_SWIRLS = 8;
    /** Where the clock keeps the carriers it last counted, for the sickness drawn between beats. */
    private static final String SICK_KEY = "Sick";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.CONTAGION;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                int outbreak = Math.max(60, ctx.duration());
                SpellEffectEntity clock = SpellEffectEntity.spawn(ctx, ctx.feet().add(0.0D, 1.0D, 0.0D), outbreak, (float) CONTACT, ctx.look());
                if (ctx.sneak()) {
                    infect(ctx.caster(), 0, outbreak, ctx.definition(), ctx.caster());
                    return CastResult.SUCCESS;
                }
                AimResolver.Result aim = ctx.aim();
                LivingEntity first = aim.living();
                if (first != null && SkillTargets.isHostile(ctx.caster(), first)) {
                    infect(first, 0, outbreak, ctx.definition(), ctx.caster());
                    SkillTargets.hurt(ctx.level(), ctx.caster(), first, 0.5F, ctx.definition(), true);
                    bloom(ctx.level(), first, VisualProfiles.of(ctx.definition()));
                    clock.serverData().putIntArray(SICK_KEY, new int[] {first.getId()});
                } else {
                    clock.serverData().putDouble("StainX", aim.point().x);
                    clock.serverData().putDouble("StainY", aim.point().y);
                    clock.serverData().putDouble("StainZ", aim.point().z);
                    clock.serverData().putInt("StainUntil", STAIN_LIFE);
                    SpellFx.decal(ctx.level(), ctx.definition(), aim.point(), aim.normal(), 1.5F);
                }
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 12.0D;
            }

            @Override
            public double aimTolerance() {
                return 0.8D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(2.0F, 12.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    private static void infect(LivingEntity target, int generation, int ticks, MagicSkillDefinition definition, Entity source) {
        MagicStatusService.apply(target, MagicStatus.INFECTED, ticks, generation, 0.0F, definition.id(), source);
    }

    private static boolean carrierOf(LivingEntity e, Entity owner) {
        MagicStatusData.Entry entry = MagicStatusService.entry(e, MagicStatus.INFECTED);
        return entry != null && owner != null && owner.getUUID().equals(entry.source());
    }

    /** The plague as matter: slow smoke in its own colour, lit by the world rather than glowing. */
    private static TintedParticleOptions miasma(int rgb, float scale) {
        return new TintedParticleOptions(MagicalParticles.WISP.get(), rgb, scale);
    }

    /** The rot, seen on every carrier it bites: a breath of the plague off the body on the beat. */
    private static void exhale(ServerLevel level, LivingEntity carrier, int rgb) {
        Vec3 c = carrier.getBoundingBox().getCenter();
        double across = carrier.getBbWidth() * 0.35D;
        level.sendParticles(miasma(rgb, CARRIER_WISP_SCALE), c.x, c.y, c.z, CARRIER_WISPS, across, carrier.getBbHeight() * 0.25D, across, 0.01D);
    }

    /** The plague's swirl: vanilla's potion swirl, in the plague's colour rather than an effect's. */
    private static ColorParticleOption swirl(int rgb) {
        return ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | (rgb & 0xFFFFFF));
    }

    /** The lancet going in: the body it infected clouds over with the plague at once. */
    private static void bloom(ServerLevel level, LivingEntity victim, VisualProfile profile) {
        Vec3 c = victim.getBoundingBox().getCenter();
        double across = victim.getBbWidth() * 0.35D;
        level.sendParticles(swirl(profile.color(ColorRole.BRIGHT)), c.x, c.y, c.z, BLOOM_SWIRLS, across, victim.getBbHeight() * 0.3D, across, 0.0D);
        exhale(level, victim, profile.color(ColorRole.BASE));
    }

    /**
     * The carriers the clock last counted, sick between beats: swirls off each body, and now and
     * then a wisp seeping low off it. Never off the caster seeded with it by a sneak cast, whose
     * own body is where their camera is.
     */
    private static void sicken(ServerLevel level, SpellEffectEntity entity, Entity owner, VisualProfile profile) {
        int[] ids = entity.serverData().getIntArray(SICK_KEY);
        if (ids.length == 0) {
            return;
        }
        ColorParticleOption plague = swirl(profile.color(ColorRole.BRIGHT));
        boolean seep = entity.tickCount % SEEP_INTERVAL == 0;
        for (int id : ids) {
            if (!(level.getEntity(id) instanceof LivingEntity carrier) || carrier == owner || !carrier.isAlive()) {
                continue;
            }
            Vec3 c = carrier.getBoundingBox().getCenter();
            double across = carrier.getBbWidth() * 0.3D;
            level.sendParticles(plague, c.x, c.y, c.z, SICK_SWIRLS, across, carrier.getBbHeight() * 0.3D, across, 0.0D);
            if (seep) {
                level.sendParticles(miasma(profile.color(ColorRole.BASE), CARRIER_WISP_SCALE), c.x, carrier.getY() + carrier.getBbHeight() * 0.25D, c.z, 1, across, 0.05D, across, 0.005D);
            }
        }
    }

    /**
     * The jump itself: a thread of the plague drawn off the carrier onto the body it just caught,
     * so an outbreak reads as spreading from someone rather than appearing on someone. Each wisp is
     * sent a little further than the last; a wisp keeps 0.93 of its speed a tick, so it travels
     * about fourteen times what it starts with.
     */
    private static void jump(ServerLevel level, LivingEntity from, LivingEntity to, int rgb) {
        Vec3 a = from.getBoundingBox().getCenter();
        Vec3 d = to.getBoundingBox().getCenter().subtract(a);
        TintedParticleOptions spore = miasma(rgb, JUMP_WISP_SCALE);
        for (int i = 0; i < JUMP_WISPS; i++) {
            double reach = 0.5D + 0.5D * (i + 1) / JUMP_WISPS;
            level.sendParticles(spore, a.x, a.y, a.z, 0, d.x, d.y, d.z, reach / 14.0D);
        }
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            Entity owner = entity.owner();
            CompoundTag data = entity.serverData();
            int remaining = entity.life() - entity.tickCount;
            int plague = VisualProfiles.of(entity.definition()).color(ColorRole.BASE);
            // the stain infects the first hostile to touch it
            if (data.contains("StainX") && entity.tickCount <= data.getInt("StainUntil")) {
                Vec3 stain = new Vec3(data.getDouble("StainX"), data.getDouble("StainY"), data.getDouble("StainZ"));
                if (entity.tickCount % STAIN_SEEP_INTERVAL == 0) {
                    // the armed stain seeps, so the trap stays visible for as long as it is armed
                    level.sendParticles(miasma(plague, STAIN_WISP_SCALE), stain.x, stain.y + 0.1D, stain.z, STAIN_WISPS, 0.4D, 0.02D, 0.4D, 0.004D);
                }
                List<LivingEntity> touching = SkillTargets.hostilesWithin(level, owner, stain, CONTACT);
                if (!touching.isEmpty()) {
                    infect(touching.get(0), 0, remaining, entity.definition(), owner);
                    data.putIntArray(SICK_KEY, new int[] {touching.get(0).getId()});
                    data.remove("StainX");
                    SpellFx.impact(level, entity.definition(), stain, new Vec3(0.0D, 1.0D, 0.0D), touching.get(0), owner, 0.8F);
                }
            }
            if (entity.tickCount % SICK_INTERVAL == 0) {
                sicken(level, entity, owner, VisualProfiles.of(entity.definition()));
            }
            if (entity.tickCount % BEAT != 0) {
                return;
            }
            List<LivingEntity> carriers = new ArrayList<>();
            AABB scan = new AABB(entity.position(), entity.position()).inflate(SCAN);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, scan, l -> l.isAlive() && carrierOf(l, owner))) {
                carriers.add(e);
            }
            if (carriers.isEmpty() && !data.contains("StainX")) {
                entity.finish();
                return;
            }
            List<LivingEntity> newborn = new ArrayList<>();
            Vec3 centroid = Vec3.ZERO;
            for (LivingEntity carrier : carriers) {
                MagicStatusData.Entry entry = MagicStatusService.entry(carrier, MagicStatus.INFECTED);
                int generation = entry != null ? entry.amplifier() : 0;
                centroid = centroid.add(carrier.position());
                if (carrier != owner) {
                    float rot = entity.damage() * (float) Math.pow(0.8D, generation);
                    carrier.invulnerableTime = 0;
                    SkillTargets.hurt(level, owner, carrier, rot, entity.definition().id());
                    exhale(level, carrier, plague);
                }
                if (generation < MAX_GENERATION) {
                    for (LivingEntity neighbour : SkillTargets.hostilesIn(level, owner, carrier.getBoundingBox().inflate(CONTACT))) {
                        if (neighbour != carrier && !carrierOf(neighbour, owner) && !newborn.contains(neighbour)) {
                            infect(neighbour, generation + 1, remaining, entity.definition(), owner);
                            newborn.add(neighbour);
                            jump(level, carrier, neighbour, plague);
                        }
                    }
                }
            }
            if (!carriers.isEmpty()) {
                entity.setPos(centroid.scale(1.0D / carriers.size()).add(0.0D, 1.0D, 0.0D));
            }
            carriers.addAll(newborn);
            int[] ids = new int[carriers.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = carriers.get(i).getId();
            }
            CompoundTag synced = new CompoundTag();
            synced.putIntArray("C", ids);
            entity.setSyncedData(synced);
            data.putIntArray(SICK_KEY, ids);
            // the outbreak's pulse on the floor under it: the clock rides a block up in the middle of
            // the carriers, and a pulse there laid its ripple flat across their thighs, in mid-air
            SpellFx.zoneTick(level, entity.definition(), SpellFx.groundBelow(level, entity.position(), 4), 0.8F);
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.VOID)
                .palette(2)
                // a plague is rot and miasma, not the void school's portal motes
                .accent(Accent.GLOOM)
                .circle(CircleScript.of(SchoolMaterial.VOID).emblem(EmblemId.PLAGUE).frame(11).band(GlyphKind.CHAIN_BAND, 12, ColorRole.INK).band(GlyphKind.DASHED_RING, 18).stamps(StampId.DOT, 12).core(CoreKind.RIPPLE, ColorRole.INK).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.custom("contagion", 1.5F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.INK_STAIN, FxKinds.Smoke.SPORE_DOTS, FxKinds.Overlay.INK_BLEED)
                .budget(2)
                .bounds(34.0F, 4.0F, 4.0F);
    }
}
