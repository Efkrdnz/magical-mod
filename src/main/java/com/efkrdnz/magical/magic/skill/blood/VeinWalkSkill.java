package com.efkrdnz.magical.magic.skill.blood;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.BloodService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.CastContext;
import com.efkrdnz.magical.magic.cast.CastResult;
import com.efkrdnz.magical.magic.cast.MobCastProfile;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.TuningView;
import com.efkrdnz.magical.magic.passive.BloodPassives;
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
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * BLOOD T-1 - the escape that only exists if something has already bled.
 *
 * <p>Every other movement skill in the mod goes where you point. This one goes to the furthest
 * blood mote in range, so a blood mage's escape route is decided several seconds earlier, by where
 * they chose to kill things. Get that wrong and the button does nothing at all.
 *
 * <p>Refusing outright rather than falling back on a short blink is the point: a fallback would
 * make the trail decorative, and the refusal is what makes players fight with one eye on their
 * own exit.
 */
public final class VeinWalkSkill implements SkillModule {

    public static final int BLOOD_COST = 12;

    /** How far out a mote can be and still be somewhere you could step to. */
    public static final double REACH = 24.0D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.VEIN_WALK;
    }

    @Override
    public SkillCastHandler handler() {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                if (!(ctx.caster() instanceof ServerPlayer player)) {
                    return CastResult.FAILED;
                }
                Optional<Vec3> destination = BloodPassives.furthestMote(player, REACH);
                if (destination.isEmpty()) {
                    player.displayClientMessage(Component.translatable("message.magical.no_blood_trail"), true);
                    // FAILED rather than a cooldown: the player pressed a button that could not have
                    // worked, and making them wait would punish the empty trail twice over.
                    return CastResult.FAILED;
                }
                if (!BloodService.pay(player, ctx.state(), BLOOD_COST)) {
                    return CastResult.FAILED;
                }
                Vec3 from = player.position();
                Vec3 to = destination.get();
                SpellEffectEntity.spawn(ctx, from.add(0.0D, 1.0D, 0.0D), 20, 0.9F, ctx.look());
                player.teleportTo(to.x, to.y, to.z);
                player.resetFallDistance();
                BloodPassives.consumeMote(player, to);
                SpellEffectEntity.spawn(ctx, to.add(0.0D, 1.0D, 0.0D), 20, 0.9F, ctx.look());
                player.serverLevel().playSound(null, player.blockPosition(),
                        SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.PLAYERS, 0.5F, 1.3F);
                return CastResult.SUCCESS;
            }

            @Override
            public double aimTolerance() {
                return 0.0D;
            }

            @Override
            public TuningView tuning() {
                return TuningView.NO_SPEED;
            }

            @Override
            public MobCastProfile mob() {
                return null;
            }
        };
    }

    @Override
    public SpellBehavior behavior() {
        // Two short-lived marks, one at each end. Nothing to drive, so nothing to tick.
        return entity -> {
            if (entity.tickCount == 1) {
                entity.setPhase(SpellEffectEntity.PHASE_ACTIVE);
            }
        };
    }

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.BLOOD)
                .circle(CircleScript.of(SchoolMaterial.BLOOD).emblem(EmblemId.KEY).frame(4)
                        .band(GlyphKind.DASHED_RING, 22, ColorRole.BRIGHT)
                        .band(GlyphKind.BRAID_BAND, 6, ColorRole.INK)
                        .stamps(StampId.LINK, 8).core(CoreKind.CROSS, ColorRole.HOT).spin(SpinSignature.ONE_WAY_FAST))
                .anchor(CircleAnchor.GROUND)
                .silhouette(Silhouette.filament(Silhouette.Form.CHAIN, FxKinds.Filament.LIQUID_ROPE, 6, 0.06F).withRole(ColorRole.HOT))
                .release(ReleaseMode.FUNNEL, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.RIPPLES, FxKinds.Smoke.DROPLET, FxKinds.Overlay.TUNNEL)
                .budget(1)
                .bounds(2.0F, 2.0F, 1.5F);
    }
}
