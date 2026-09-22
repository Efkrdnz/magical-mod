package com.efkrdnz.magical.magic.skill.sword;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.SkillCastRegistry;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.sword.SwordService;
import com.efkrdnz.magical.magic.sword.stance.SwordStance;
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
import com.efkrdnz.magical.magic.visual.TierProfile;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * SWORD T-3 - choose how the swords stand.
 *
 * <p>A hold: the six stances appear in a row, the wheel walks them, release takes one. That is
 * the whole of the class's authoring surface now, and it is one decision rather than twelve.
 *
 * <p>This key used to be The Bearing - a hold that read back the lattice Call the Blade had
 * written and let a wielder <em>unwrite</em> a bearing they could not look at. It existed because
 * half of an authored Array sat behind the wielder's head where the crosshair could not reach it,
 * which is the clearest possible statement of why the authoring had to go: a feature whose only
 * job is to undo another feature's blind spot. Six designed stances have no blind spot, so the
 * key that used to remove things now chooses between them.
 *
 * <p>Hold-gated, so a press only prints the hint and the real work arrives on release through
 * {@code SetStancePayload}. That means {@code castViaRegistry} returns before it resolves a stat,
 * spends a point of mana, casts an aim ray or starts a clock - {@code ctx.aim()} is <b>null</b> in
 * the press handler, and the twenty ticks of cooldown on the definition are decoration until
 * {@link #take} takes them through {@code SwordService.payFor}.
 *
 * <p><b>Opening the picker is free and closing it is free.</b> Only a release that actually
 * changes the stance is charged, which is why the change is established before {@code payFor} is
 * reached: looking at your own options is not a power, and a wielder who cannot afford to look is
 * a wielder who cannot learn the class.
 */
public final class SwordStanceSkill implements SkillModule {

    /** Pressing the slot says this; the overlay is what the key is actually for. */
    public static final String HOLD_HINT_KEY = "message.magical.stance_hold";

    /** Said on a release that named a stance this rung has not opened. */
    public static final String KEY_LOCKED = "message.magical.stance_locked";

    /**
     * The mark this skill wears on its circle.
     *
     * <p>{@code AZIMUTH} - a bearing rose - which was chosen for the old hold because it read a
     * direction back, and is if anything more apt for a picker whose six entries are six ways of
     * standing. Held in one constant so a dedicated cell is a one-word change here.
     */
    private static final EmblemId EMBLEM = EmblemId.AZIMUTH;

    /** Frame sides, unique within the SWORD school. Its six skills take 3..8 in kit order. */
    private static final int FRAME_SIDES = 4;

    /**
     * The cast circle's radius in blocks, and it is a property of the screen rather than of the
     * tier.
     *
     * <p>An {@code EYE_FORWARD} circle is not placed in the world at all: {@code SpellFx.windup}
     * hangs it {@link #HAND_DISTANCE} blocks along the look from the eye and turns it to face
     * the camera, so its size on screen is {@code atan(radius / HAND_DISTANCE)} and nothing
     * else. Every tier below zero resolves to {@code TierProfile.forTier(4)} and its radius of
     * 3.0 is <b>73 degrees</b> there - a disc that runs off all four edges of a default
     * seventy-degree frame, which is exactly the picture the first Loose capture returned. The
     * ceiling is the frame itself, {@code HAND_DISTANCE * tan(35 degrees)} = 0.63; 0.28 is
     * seventeen degrees, half the half-frame, so the ring sits in the middle of the shot with
     * the world still visible round it.
     *
     * <p>This circle does not draw today - see {@link #handler()}: the skill is hold-gated, so
     * {@code castViaRegistry} returns before {@code SpellFx.windup} is ever reached. The number
     * is right anyway, because the day somebody gives the hold a windup is not the day to
     * discover the tier default.
     */
    public static final float HAND_RING_RADIUS = 0.28F;

    /** Where {@code SpellFx.windup} puts an {@code EYE_FORWARD} circle: along the look from the eye. */
    public static final double HAND_DISTANCE = 0.9D;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.SWORD_STANCE;
    }

    @Override
    public SkillCastHandler handler() {
        // holdHint answers MobCastProfile.NONE from the interface default, which is the answer
        // this skill wants: a mob has no stance to choose.
        return SkillCastRegistry.holdHint(HOLD_HINT_KEY);
    }

    // ---- the release -------------------------------------------------------------------------

    /**
     * Takes the stance the release named, or refuses it.
     *
     * <p>Every question is asked again here, because the ordinal arrived from a client: that the
     * wielder has the skill at all, that the ordinal names a real stance, and that their rung has
     * opened it. {@link SwordStance#byOrdinal} clamps rather than throwing, so a forged packet
     * naming stance 99 is Guard and not an exception inside a network handler - and the rung is
     * then checked against the clamped value, which refuses rather than clamping, so the picker
     * can say which rule was broken.
     *
     * <p>Order matters in one place only, and it is the usual one: the change is established
     * before {@code payFor}, so a release that names the stance already held, or one this rung
     * has not opened, costs nothing and starts no clock.
     *
     * <p>Nothing here draws or sheathes. The stance is a property of the wielder rather than of
     * the steel, so it can be chosen with the swords away and is waiting when they come out -
     * which is also why changing it while they are out is instant: {@code Formation.place} is
     * run fresh by both sides every tick, so the swords simply are somewhere else the next frame.
     *
     * @return whether the release did anything, which is also whether it was charged
     */
    public static boolean take(ServerPlayer player, int ordinal) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasUnlocked(MagicContent.SWORD_STANCE.id())) {
            return false;
        }
        SwordStance wanted = SwordStance.byOrdinal(ordinal);
        if (wanted == state.swordArray().stance()) {
            return false;
        }
        if (!state.swordArray().rules().allows(wanted)) {
            player.displayClientMessage(Component.translatable(KEY_LOCKED,
                    Component.translatable(wanted.nameKey())), true);
            return false;
        }
        if (!SwordService.payFor(player, state, MagicContent.SWORD_STANCE, -1)) {
            return false;
        }
        if (!state.swordArray().setStance(wanted)) {
            return false;
        }
        SwordService.tendArrayEntity(player, state);
        state.sync(player);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND,
                SoundSource.PLAYERS, 0.35F, 1.7F);
        return true;
    }

    /** The hint the press prints, exposed so the overlay and the input arm read one string. */
    public static Component holdHint() {
        return Component.translatable(HOLD_HINT_KEY);
    }

    // ---- the look ------------------------------------------------------------------------------

    @Override
    public VisualProfile.Builder profile() {
        return VisualProfile.builder(definition())
                .material(SchoolMaterial.SWORD)
                .circle(CircleScript.of(SchoolMaterial.SWORD).emblem(EMBLEM).frame(FRAME_SIDES)
                        .band(GlyphKind.DASHED_RING, 24, ColorRole.BRIGHT)
                        // Six spokes, one per stance, where the old hold had twelve for twelve
                        // lattice bearings. The ring is the choice it opens.
                        .spokes(6, 0.22F, true)
                        .core(CoreKind.IRIS, ColorRole.HOT).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .tier(TierProfile.forTier(definition().tier()).withRadius(HAND_RING_RADIUS))
                // Nothing carries this profile onto an entity, so this silhouette is never
                // painted - but there is no painter registered under "sword_stance" either, and
                // a miss draws nothing and warns. An empty mode mask is the same insurance Call
                // the Blade takes, where that fallback was live.
                .silhouette(Silhouette.custom("sword_stance", 1.0F).forModes())
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.CLOCK_SPOKES, FxKinds.Smoke.RUNE_MOTE, FxKinds.Overlay.HEX_PULSE)
                .budget(1)
                .bounds(1.5F, 1.5F, 1.5F);
    }
}
