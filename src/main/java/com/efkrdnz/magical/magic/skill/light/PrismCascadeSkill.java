package com.efkrdnz.magical.magic.skill.light;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * LIGHT T3 - RICOCHET / HITSCAN_LOOK_COMMITTED / REFLECTING_POLYLINE. The aim is committed on
 * press: the ray reflects off block faces up to four times; the path shows as hairline seams
 * during the windup, then light floods the polyline, hurting once everything along it, and stays
 * as hardlight for a while. A skill-shot banked off walls.
 */
public final class PrismCascadeSkill implements SkillModule {
    private static final int WINDUP = 11;
    private static final int FLOOD = 6;
    private static final int MAX_BOUNCES = 4;
    private static final double MAX_LENGTH = 64.0D;
    private static final double FIRST_LEG = 40.0D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.PRISM_CASCADE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                List<Vec3> path = trace(ctx.level(), ctx.eye(), ctx.look(), ctx.caster());
                int hold = Math.max(10, ctx.duration());
                SpellEffectEntity cascade = SpellEffectEntity.spawn(ctx, ctx.eye().add(0.0D, -0.1D, 0.0D), WINDUP + FLOOD + hold, Math.max(0.6F, ctx.size()), ctx.look());
                cascade.setExtra(hold);
                CompoundTag data = new CompoundTag();
                ListTag points = new ListTag();
                for (Vec3 p : path) {
                    CompoundTag pt = new CompoundTag();
                    pt.putDouble("X", p.x - cascade.getX());
                    pt.putDouble("Y", p.y - cascade.getY());
                    pt.putDouble("Z", p.z - cascade.getZ());
                    points.add(pt);
                }
                data.put("Points", points);
                cascade.setSyncedData(data);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public int counterWindowTicks() {
                return WINDUP;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.attack(3.0F, 30.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    private static List<Vec3> trace(ServerLevel level, Vec3 from, Vec3 dir, net.minecraft.world.entity.Entity caster) {
        List<Vec3> points = new ArrayList<>();
        points.add(from);
        Vec3 pos = from;
        Vec3 d = dir.normalize();
        double remaining = MAX_LENGTH;
        for (int bounce = 0; bounce <= MAX_BOUNCES && remaining > 0.5D; bounce++) {
            double leg = Math.min(remaining, bounce == 0 ? FIRST_LEG : MAX_LENGTH);
            BlockHitResult hit = level.clip(new ClipContext(pos, pos.add(d.scale(leg)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (hit.getType() != HitResult.Type.BLOCK) {
                points.add(pos.add(d.scale(leg)));
                break;
            }
            Vec3 n = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
            Vec3 p = hit.getLocation().add(n.scale(0.02D));
            remaining -= p.distanceTo(pos);
            points.add(p);
            d = d.subtract(n.scale(2.0D * d.dot(n))).normalize();
            pos = p;
        }
        return points;
    }

    static List<Vec3> points(SpellEffectEntity entity) {
        List<Vec3> out = new ArrayList<>();
        ListTag list = entity.syncedData().getList("Points", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag pt = list.getCompound(i);
            out.add(entity.position().add(pt.getDouble("X"), pt.getDouble("Y"), pt.getDouble("Z")));
        }
        return out;
    }

    @Override
    public SpellBehavior behavior() {
        return entity -> {
            ServerLevel level = entity.serverLevel();
            List<Vec3> points = points(entity);
            if (points.size() < 2) {
                return;
            }
            if (entity.tickCount == WINDUP) {
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                Set<Integer> struck = new HashSet<>();
                for (int i = 0; i + 1 < points.size(); i++) {
                    Vec3 a = points.get(i);
                    Vec3 b = points.get(i + 1);
                    Vec3 seg = b.subtract(a);
                    double len = seg.length();
                    if (len < 1.0E-3D) {
                        continue;
                    }
                    Vec3 dir = seg.scale(1.0D / len);
                    for (LivingEntity hostile : SkillTargets.hostilesIn(level, entity.owner(), new AABB(a, b).inflate(entity.radius() + 0.5D))) {
                        Vec3 rel = hostile.getBoundingBox().getCenter().subtract(a);
                        double along = rel.dot(dir);
                        if (along < 0.0D || along > len || rel.subtract(dir.scale(along)).length() > entity.radius() + hostile.getBbWidth() * 0.5D || !struck.add(hostile.getId())) {
                            continue;
                        }
                        SkillTargets.hurt(level, entity.owner(), hostile, entity.damage(), entity.definition(), true);
                        hostile.push(dir.x * 0.4D * Math.max(0.5D, entity.knockback()), 0.1D, dir.z * 0.4D * Math.max(0.5D, entity.knockback()));
                        hostile.hurtMarked = true;
                    }
                    SpellFx.impact(level, entity.definition(), b, dir.scale(-1.0D), null, entity.owner(), 0.9F);
                }
            } else if (entity.tickCount > WINDUP + FLOOD && entity.tickCount % 10 == 0) {
                for (int i = 0; i + 1 < points.size(); i++) {
                    Vec3 a = points.get(i);
                    Vec3 b = points.get(i + 1);
                    Vec3 seg = b.subtract(a);
                    double len = seg.length();
                    if (len < 1.0E-3D) {
                        continue;
                    }
                    Vec3 dir = seg.scale(1.0D / len);
                    for (LivingEntity hostile : SkillTargets.hostilesIn(level, entity.owner(), new AABB(a, b).inflate(entity.radius()))) {
                        Vec3 rel = hostile.getBoundingBox().getCenter().subtract(a);
                        double along = rel.dot(dir);
                        if (along >= 0.0D && along <= len && rel.subtract(dir.scale(along)).length() <= entity.radius() + hostile.getBbWidth() * 0.5D) {
                            SkillTargets.hurt(level, entity.owner(), hostile, 3.0F, entity.definition().id());
                        }
                    }
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.LIGHT)
                .circle(CircleScript.of(SchoolMaterial.LIGHT).emblem(EmblemId.ARROW).frame(5).band(GlyphKind.RUNE_BAND, 14).stamps(StampId.FEATHER_ARC, 5).star(10, 3).spokes(5, 0.3F, true).orbit(5, 0.84F, 5).core(CoreKind.SUNBURST).stack(2, 0.35F).spin(SpinSignature.COUNTER_FAST))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.custom("prism_cascade", 40.0F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_RECOIL)
                .impact(FxKinds.Mark.RAY_BURST, FxKinds.Smoke.LENS_SPARKLE, FxKinds.Overlay.PRISM_RING)
                .bounds(64.0F, 8.0F, 8.0F);
    }
}
