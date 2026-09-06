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
import java.util.ArrayList;
import java.util.List;
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

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            Entity owner = entity.owner();
            CompoundTag data = entity.serverData();
            int remaining = entity.life() - entity.tickCount;
            // the stain infects the first hostile to touch it
            if (data.contains("StainX") && entity.tickCount <= data.getInt("StainUntil")) {
                Vec3 stain = new Vec3(data.getDouble("StainX"), data.getDouble("StainY"), data.getDouble("StainZ"));
                List<LivingEntity> touching = SkillTargets.hostilesWithin(level, owner, stain, CONTACT);
                if (!touching.isEmpty()) {
                    infect(touching.get(0), 0, remaining, entity.definition(), owner);
                    data.remove("StainX");
                    SpellFx.impact(level, entity.definition(), stain, new Vec3(0.0D, 1.0D, 0.0D), touching.get(0), owner, 0.8F);
                }
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
                }
                if (generation < MAX_GENERATION) {
                    for (LivingEntity neighbour : SkillTargets.hostilesIn(level, owner, carrier.getBoundingBox().inflate(CONTACT))) {
                        if (neighbour != carrier && !carrierOf(neighbour, owner) && !newborn.contains(neighbour)) {
                            infect(neighbour, generation + 1, remaining, entity.definition(), owner);
                            newborn.add(neighbour);
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
            SpellFx.zoneTick(level, entity.definition(), entity.position(), 0.8F);
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.VOID)
                .palette(2)
                .circle(CircleScript.of(SchoolMaterial.VOID).emblem(EmblemId.PLAGUE).frame(11).band(GlyphKind.CHAIN_BAND, 12, ColorRole.INK).band(GlyphKind.DASHED_RING, 18).stamps(StampId.DOT, 12).core(CoreKind.RIPPLE, ColorRole.INK).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.custom("contagion", 1.5F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.INK_STAIN, FxKinds.Smoke.SPORE_DOTS, FxKinds.Overlay.INK_BLEED)
                .budget(2)
                .bounds(34.0F, 4.0F, 4.0F);
    }
}
