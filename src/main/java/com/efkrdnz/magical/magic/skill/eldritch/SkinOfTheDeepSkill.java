package com.efkrdnz.magical.magic.skill.eldritch;

import com.efkrdnz.magical.entity.fx.EldritchConstructEntity;
import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.EldritchService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.MagicSkillTuning;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.service.SkillTargets;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.status.MagicStatus;
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
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * ELDRITCH T-5 - small tentacles grow from your back and take the hits meant for you.
 *
 * <p>One ward per hit: it absorbs the whole hit, dissolves, and bites the thing that struck if it
 * is within reach. While any ward stands the wearer cannot be moved. The construct rides its
 * owner and its {@code extra} is the count of wards left; the renderer draws that many across the
 * back and hides them all from the wearer in first person, like the coagulate shell.
 */
public final class SkinOfTheDeepSkill implements SkillModule {
    public static final int WARDS_PER_SIZE = 4;
    public static final int MIN_WARDS = 2;
    public static final int MAX_WARDS = 8;
    public static final double BITE_REACH = 4.0D;
    private static final int IMMOVABLE_TOP_UP = 15;
    private static final String KEY_POTENCY = "potency";

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SKIN_OF_THE_DEEP;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                EldritchConstructEntity standing = worn(player);
                if (standing != null) {
                    standing.finish();
                }
                float potency = EldritchService.potency(ctx.state());
                int wards = Math.max(MIN_WARDS, Math.min(MAX_WARDS, Math.round(WARDS_PER_SIZE * ctx.size())));
                EldritchService.notice(player, ctx.state(), EldritchService.cost(ctx.stats()));
                EldritchConstructEntity skin = EldritchConstructEntity.spawn(ctx, EldritchConstructEntity.MODEL_TENTACLE,
                        EldritchConstructEntity.ANCHOR_OWNER, ctx.feet(), ctx.duration(), (float) BITE_REACH, 0.35F * potency, ctx.look());
                skin.setExtra(wards);
                skin.serverData().putFloat(KEY_POTENCY, potency);
                skin.setPhase(SpellEffectEntity.PHASE_ACTIVE);
                ctx.level().playSound(null, player.blockPosition(), SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.0F, 0.8F);
                return CastResult.SUCCESS;
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED.labels("screen.magical.tuning.bite", null,
                        "screen.magical.tuning.wards", "screen.magical.tuning.wear", "screen.magical.tuning.thrift");
            }
        };
    }

    /** The skin this player wears with wards left, or null. */
    public static EldritchConstructEntity worn(ServerPlayer player) {
        for (EldritchConstructEntity skin : EldritchConstructEntity.ownedBy(player.serverLevel(), player, MagicContent.SKIN_OF_THE_DEEP.id(), 4.0D)) {
            if (skin.extra() > 0) {
                return skin;
            }
        }
        return null;
    }

    /** A ward takes the whole hit and bites back. Returns what gets through: nothing, or all of it. */
    public static float absorb(ServerPlayer player, PlayerMagicState state, DamageSource source, float amount) {
        if (amount <= 0.0F) {
            return amount;
        }
        EldritchConstructEntity skin = worn(player);
        if (skin == null) {
            return amount;
        }
        skin.setExtra(skin.extra() - 1);
        float potency = skin.serverData().contains(KEY_POTENCY) ? skin.serverData().getFloat(KEY_POTENCY) : 1.0F;
        if (source.getEntity() instanceof LivingEntity attacker && attacker != player && attacker.distanceTo(player) <= BITE_REACH) {
            SkillTargets.hurt(player.serverLevel(), player, attacker, skin.damage() * potency, skin.skillId());
        }
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.SCULK_CLICKING, SoundSource.PLAYERS, 0.8F, 0.6F);
        if (skin.extra() <= 0) {
            skin.finish();
        }
        return 0.0F;
    }

    @Override
    public SpellBehavior behavior() {
        return new SpellBehavior() {
            @Override
            public void tick(SpellEffectEntity effect) {
                if (!(effect instanceof EldritchConstructEntity skin) || !(skin.owner() instanceof ServerPlayer owner) || !owner.isAlive() || skin.extra() <= 0) {
                    effect.finish();
                    return;
                }
                skin.setPos(owner.getX(), owner.getY(), owner.getZ());
                if (skin.tickCount % 10 == 1) {
                    MagicStatusService.apply(owner, MagicStatus.IMMOVABLE, IMMOVABLE_TOP_UP, skin.skillId(), owner);
                }
            }

            @Override
            public void onExpire(SpellEffectEntity effect) {
                if (effect.owner() instanceof LivingEntity owner) {
                    MagicStatusService.clear(owner, MagicStatus.IMMOVABLE);
                }
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.ELDRITCH)
                .circle(CircleScript.of(SchoolMaterial.ELDRITCH).emblem(EmblemId.SCALES).frame(8)
                        .band(GlyphKind.PETAL_BAND, 12, ColorRole.BRIGHT)
                        .band(GlyphKind.CHAIN_BAND, 10, ColorRole.INK)
                        .stamps(StampId.HEX, 8).core(CoreKind.HEX_LENS, ColorRole.HOT).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.field(Silhouette.Form.DOME, FxKinds.Field.ORGANIC_CELLS, 1.1F, 2.0F).withRole(ColorRole.DIM))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_BLOOM)
                .impact(FxKinds.Mark.HEX_CELLS, FxKinds.Smoke.SPORE_DOTS, FxKinds.Overlay.HEX_PULSE)
                .budget(3)
                .bounds(2.0F, 2.5F, 1.0F);
    }
}
