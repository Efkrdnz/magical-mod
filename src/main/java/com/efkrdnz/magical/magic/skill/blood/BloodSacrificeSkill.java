package com.efkrdnz.magical.magic.skill.blood;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.blood.BloodSacrificeService;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
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
import com.efkrdnz.magical.magic.visual.SpinSignature;
import com.efkrdnz.magical.magic.visual.StampId;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * BLOOD T-1 - bargain with yourself.
 *
 * <p>The one blood ability that will not take a heart instead. It wants a full Crimson Vessel and
 * refuses anything less, so the whole of it has to be earned in the field before it can be spent:
 * pressing the key on a half-full Vessel says so and stops.
 *
 * <p>Pressing it opens the pact screen and nothing more. What it costs is charged when the pact is
 * sealed, and so is the cooldown, which is why this returns {@link CastResult#HANDLED}: opening
 * the screen and walking away has to be free, or the ability would punish reading it.
 */
public final class BloodSacrificeSkill implements SkillModule {

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.BLOOD_SACRIFICE;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                if (ctx.state().bloodVessel() < BloodSacrificeService.RITUAL_COST) {
                    // Checked here as well as at the seal so a short Vessel costs a keypress rather
                    // than a walk through the whole screen.
                    player.displayClientMessage(Component.translatable("message.magical.vessel_not_full"), true);
                    return CastResult.FAILED;
                }
                BloodSacrificeService.open(player, ctx.state());
                return CastResult.HANDLED;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public TuningView tuning() {
                // Size buys points to spend in the screen; duration buys the clock on both halves.
                // Nothing here is aimed, and the price is flat, so there is no reach and no thrift.
                return new TuningView(false, false, true, true, false, null,
                        null, "screen.magical.tuning.pact",
                        "screen.magical.tuning.rite", null);
            }

            @Override
            public MobCastProfile mob() {
                return null;
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.BLOOD)
                // Every one of these is unique within the school or across the whole roster, and a
                // test fails the build if it is not: the flame nobody else claimed for the bargain,
                // a seven-sided frame no other blood circle wears, and the hourglass for the two
                // clocks the pact starts.
                .circle(CircleScript.of(SchoolMaterial.BLOOD).emblem(EmblemId.FLAME).frame(7)
                        .band(GlyphKind.RUNE_BAND, 9, ColorRole.BRIGHT)
                        .band(GlyphKind.CHAIN_BAND, 6, ColorRole.DIM)
                        .stamps(StampId.HOURGLASS, 6).core(CoreKind.IRIS, ColorRole.HOT).spin(SpinSignature.STATIC))
                .anchor(CircleAnchor.GROUND)
                .throughTerrain(true)
                .silhouette(Silhouette.custom("blood_sacrifice", 2.0F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.SHOCK_RING, FxKinds.Smoke.DROPLET, FxKinds.Overlay.VIGNETTE)
                .budget(2)
                .bounds(2.0F, 2.0F, 1.0F);
    }
}
