package com.efkrdnz.magical.magic.skill.spatial;

import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.SafeSpotSearch;
import com.efkrdnz.magical.magic.service.SkillTargets;
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
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * SPATIAL T0 - SWAP / AIM_POINT / PAIR_EXCHANGE. The two hostiles nearest the aim point exchange
 * positions and facing on the release tick, keeping their own velocity. A lone hostile swaps with
 * a stake at the aim point. Sneak = mirror the aim point through yourself.
 */
public final class TranspositionSkill implements SkillModule {
    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.TRANSPOSITION;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                ServerLevel level = ctx.level();
                Vec3 point = ctx.aim().point();
                if (ctx.sneak()) {
                    point = ctx.feet().add(ctx.feet().subtract(point));
                }
                List<LivingEntity> picks = new ArrayList<>(SkillTargets.hostilesWithin(level, ctx.caster(), point, 6.0D));
                if (picks.size() < 2) {
                    for (LivingEntity near : SkillTargets.hostilesWithin(level, ctx.caster(), ctx.feet(), 6.0D)) {
                        if (!picks.contains(near)) {
                            picks.add(near);
                        }
                        if (picks.size() >= 2) {
                            break;
                        }
                    }
                }
                if (picks.isEmpty()) {
                    SpellFx.decal(level, ctx.definition(), point, new Vec3(0.0D, 1.0D, 0.0D), 0.8F);
                    return CastResult.CONSUMED_NO_COOLDOWN;
                }
                LivingEntity a = picks.get(0);
                LivingEntity b = picks.size() > 1 ? picks.get(1) : null;
                Vec3 posA = a.position();
                float yawA = a.getYRot();
                float pitchA = a.getXRot();
                Vec3 posB;
                float yawB;
                float pitchB;
                if (b != null) {
                    posB = b.position();
                    yawB = b.getYRot();
                    pitchB = b.getXRot();
                } else {
                    Vec3 stake = SafeSpotSearch.standableNear(level, point, 2, 3, a.getBbWidth(), a.getBbHeight());
                    posB = stake != null ? stake : point;
                    yawB = yawA;
                    pitchB = pitchA;
                }
                Vec3 destA = SafeSpotSearch.liftClear(level, posB, a.getBbWidth(), a.getBbHeight(), 2.0D);
                SafeSpotSearch.place(a, destA != null ? destA : posB, yawB, pitchB, true);
                if (b != null) {
                    Vec3 destB = SafeSpotSearch.liftClear(level, posA, b.getBbWidth(), b.getBbHeight(), 2.0D);
                    SafeSpotSearch.place(b, destB != null ? destB : posA, yawA, pitchA, true);
                }
                double pop = Math.max(0.05D, ctx.stats().knockback());
                for (LivingEntity swapped : b != null ? List.of(a, b) : List.of(a)) {
                    swapped.setDeltaMovement(swapped.getDeltaMovement().add(0.0D, pop, 0.0D));
                    swapped.hurtMarked = true;
                    SkillTargets.hurt(level, ctx.caster(), swapped, ctx.damage(), ctx.definition(), true);
                }
                // the stitch between the two exchanged spots
                Vec3 mid = posA.add(posB).scale(0.5D);
                SpellEffectEntity stitch = SpellEffectEntity.spawn(ctx, mid, 14, 1.0F, new Vec3(0.0D, 1.0D, 0.0D));
                CompoundTag tag = new CompoundTag();
                tag.putDouble("AX", posA.x);
                tag.putDouble("AY", posA.y);
                tag.putDouble("AZ", posA.z);
                tag.putDouble("BX", posB.x);
                tag.putDouble("BY", posB.y);
                tag.putDouble("BZ", posB.z);
                stitch.setSyncedData(tag);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 18.0D;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.mobility(4.0F, 18.0F);
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SPATIAL)
                .circle(CircleScript.of(SchoolMaterial.SPATIAL).emblem(EmblemId.CHIASMA).frame(7).band(GlyphKind.BRAID_BAND, 7).band(GlyphKind.TICK_BAND, 28, ColorRole.DIM).stamps(StampId.CHEVRON, 7).mirror(2).core(CoreKind.CROSS).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.AIM_SURFACE)
                .silhouette(Silhouette.custom("transposition", 6.0F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.LATTICE_GRID, FxKinds.Smoke.HEX_FRAGMENT, FxKinds.Overlay.HEX_PULSE)
                .bounds(8.0F, 3.0F, 3.0F);
    }
}
