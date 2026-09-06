package com.efkrdnz.magical.magic.skill.water;

import com.efkrdnz.magical.entity.fx.SolidConstructEntity;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * WATER T1 - DROWN / AIM_TARGET_HITSCAN / HEAD_SPHERE. A globe of still water seals over the
 * victim's head with its own breath ledger; when it runs dry the victim takes armour-bypassing
 * drowning pulses. The globe is a hittable body anyone else can pop. A miss leaves it hovering,
 * waiting for something to walk into it. Sneak = hold it at arm's length in front of you.
 */
public final class DrowningBellSkill implements SkillModule {
    private static final int BREATH = 300;
    private static final byte MODE_GLOBE = 2;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.DROWNING_BELL;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                LivingEntity victim = ctx.aim().living();
                boolean valid = victim != null && SkillTargets.isHostile(ctx.caster(), victim) && !ctx.sneak();
                Vec3 pos = valid ? victim.getEyePosition() : ctx.sneak() ? ctx.eye().add(ctx.look().scale(0.9D)) : ctx.aim().point();
                int life = Math.max(40, ctx.duration());
                SpellEffectEntity template = SpellEffectEntity.create(ctx.level(), ctx.definition(), ctx.stats(), ctx.caster(), pos, valid ? life : life + 40, Math.max(0.5F, ctx.size() * 0.5F), ctx.look(), (int) (ctx.seed() & 63));
                template.setMode((byte) (MODE_GLOBE | (ctx.sneak() ? 1 : 0)));
                SolidConstructEntity globe = SolidConstructEntity.create(ctx.level(), template, pos, 0.9F, 0.9F, 1.0F, 0);
                globe.setSolid(false);
                globe.setMode((byte) (MODE_GLOBE | (ctx.sneak() ? 1 : 0)));
                globe.setExtra(BREATH);
                if (valid) {
                    globe.setTarget(victim);
                }
                ctx.level().addFreshEntity(globe);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimRange() {
                return 14.0D;
            }

            @Override
            public double aimTolerance() {
                return 0.8D;
            }

            @Override
            public MobCastProfile mob() {
                return MobCastProfile.control(2.0F, 14.0F);
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
                Entity target = entity.target();
                Entity owner = entity.owner();
                if (target instanceof LivingEntity victim && victim.isAlive()) {
                    entity.setPos(victim.getEyePosition());
                    int breath = entity.extra() - 5;
                    entity.setExtra(breath);
                    entity.setValue(Math.max(0.0F, breath / (float) BREATH));
                    if (victim instanceof Mob mob) {
                        mob.setTarget(null);
                        mob.getNavigation().stop();
                    }
                    if (victim instanceof ServerPlayer player) {
                        player.setAirSupply(Math.max(-19, Math.round(breath / (float) BREATH * player.getMaxAirSupply())));
                    }
                    if (breath <= 0 && entity.tickCount % 5 == 0) {
                        SkillTargets.hurt(level, owner, victim, entity.damage(), entity.definition().id());
                        victim.invulnerableTime = 0;
                    }
                    return;
                }
                if (target != null) {
                    entity.finish(); // victim gone
                    return;
                }
                // hovering: at arm's length while sneaking, else waiting at the ray end
                if (entity.sneakMode() && owner instanceof LivingEntity living) {
                    entity.setPos(living.getEyePosition().add(living.getLookAngle().scale(0.9D)));
                }
                for (LivingEntity hostile : SkillTargets.hostilesWithin(level, owner, entity.position(), 0.9D)) {
                    entity.setTarget(hostile);
                    entity.setLife(entity.tickCount + Math.max(40, entity.duration()));
                    SpellFx.impact(level, entity.definition(), hostile.getEyePosition(), new Vec3(0.0D, 1.0D, 0.0D), hostile, owner, 0.7F);
                    break;
                }
            }

            @Override
            public void onExpire(SpellEffectEntity entity) {
                if (entity.target() instanceof ServerPlayer player) {
                    player.setAirSupply(player.getMaxAirSupply());
                }
                SpellFx.impact(entity.serverLevel(), entity.definition(), entity.position(), new Vec3(0.0D, 1.0D, 0.0D), null, entity.owner(), 0.8F);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.WATER)
                .circle(CircleScript.of(SchoolMaterial.WATER).emblem(EmblemId.BELL).frame(9).band(GlyphKind.CHAIN_BAND, 18).stamps(StampId.TEARDROP, 9).core(CoreKind.RIPPLE).spin(SpinSignature.COUNTER_SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.field(Silhouette.Form.SPHERE, FxKinds.Field.RIPPLE_WATER, 0.55F, 0.55F, 4, 6).withOpacity(0.75F))
                .silhouette(Silhouette.orb(Silhouette.Form.BILLBOARD, FxKinds.Orb.HOLLOW_SHELL, 0.25F, 4, 8).withOffset(0.55F))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.DROPLET, FxKinds.Overlay.TUNNEL)
                .bounds(2.0F, 2.0F, 2.0F);
    }
}
