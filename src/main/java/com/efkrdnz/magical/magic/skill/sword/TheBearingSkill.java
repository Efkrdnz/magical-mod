package com.efkrdnz.magical.magic.skill.sword;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.SkillCastRegistry;
import com.efkrdnz.magical.magic.skill.SkillModule;
import com.efkrdnz.magical.magic.sword.SwordArray;
import com.efkrdnz.magical.magic.sword.SwordService;
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
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * SWORD T-3 - read the shape you authored, and unwrite a bearing you cannot look at.
 *
 * <p>A hold, and it exists for one reason that is not "the kit needs a screen": <b>six of your
 * stations are behind your head, and the crosshair cannot reach them.</b> Call the Blade writes
 * with the crosshair and nothing else in the class writes at all, so without this the half of the
 * Array behind the wielder is unreadable and permanent.
 *
 * <p>Hold-gated, so a press only prints the hint and the real work arrives on release through
 * {@code PullStationsPayload}. That means {@code castViaRegistry} returns before it resolves a
 * stat, spends a point of mana, casts an aim ray or starts a clock - {@code ctx.aim()} is
 * <b>null</b> in the press handler, and the twenty ticks of cooldown on the definition are
 * decoration until {@link #pull} takes them through {@code SwordService.payFor}.
 *
 * <p>The pull is the only thing in the kit that takes a bearing off the Array. Ward and a shed
 * empty a station; only this removes one - which is why the Bearing can dismantle a build and
 * never make one, and why its Edge comes back <em>loose</em> rather than spent.
 */
public final class TheBearingSkill implements SkillModule {

    /** Pressing the slot says this; the overlay is what the key is actually for. */
    public static final String HOLD_HINT_KEY = "message.magical.bearing_hold";

    /**
     * Bits in a pull mask, one per slot, which is {@link SwordArray#MAX_STATIONS}.
     *
     * <p>Twelve is a full ring at the minimum separation and there is no thirteenth station, so
     * the mask can never need a wider word. Every bit above the live station count is discarded
     * on arrival, so a forged packet writes nothing.
     */
    public static final int MASK_BITS = SwordArray.MAX_STATIONS;

    private static final int MASK = (1 << MASK_BITS) - 1;

    /**
     * The mark this skill wears on its circle.
     *
     * <p>The design asks for a marked disc, and {@code GEAR} is the one emblem cell in the atlas
     * nothing else had claimed - a toothed disc, which is very nearly the mark asked for. Held in
     * one constant so a dedicated cell is a one-word change here.
     */
    private static final EmblemId EMBLEM = EmblemId.AZIMUTH;

    /** Frame sides, unique within the SWORD school. Its six skills take 3..8 in kit order. */
    private static final int FRAME_SIDES = 4;

    @Override
    public MagicSkillDefinition definition() {
        return MagicContent.THE_BEARING;
    }

    @Override
    public SkillCastHandler handler() {
        // holdHint answers MobCastProfile.NONE from the interface default, which is the answer
        // this skill wants: a mob has no Array to read.
        return SkillCastRegistry.holdHint(HOLD_HINT_KEY);
    }

    // ---- the release -------------------------------------------------------------------------

    /**
     * Commits a whole release at once: every marked station goes, and its Edge is loose again.
     *
     * <p>Three things in the order they happen, and the order is the point.
     *
     * <ol>
     *   <li><b>Every bit is re-checked against the live station count.</b> The mask arrives from a
     *       client and the count is the server's, so a bit naming a slot that is not there is
     *       dropped rather than clamped onto a neighbour - a clamp would let a forged packet pull
     *       a bearing the wielder never marked.
     *   <li><b>A release that pulls nothing is refused and not charged.</b> Which is why the
     *       count is taken before {@code payFor} rather than after it: the hold is free to open,
     *       free to read and free to close again, and only a release that does something starts
     *       the clock.
     *   <li><b>The pulls run in descending slot order.</b> {@link SwordArray#pull} removes from a
     *       list, so every slot above the one that went renumbers; walking down means no slot
     *       still to be pulled has moved by the time it is reached.
     * </ol>
     *
     * <p>Nothing here touches the spent Edge. A station's metal is in the station, so removing it
     * lowers {@code bound()} and {@code whole - bound - spent} rises by exactly that much -
     * conservation holds without anybody adding anything up. A blade that had already left this
     * bearing is spent metal in the world and is not the station's to hand back.
     *
     * @return whether the release did anything, which is also whether it was charged
     */
    public static boolean pull(ServerPlayer player, int mask) {
        PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
        if (!state.hasUnlocked(MagicContent.THE_BEARING.id())) {
            return false;
        }
        SwordArray array = state.swordArray();
        int wanted = mask & MASK & ((1 << Math.min(array.size(), MASK_BITS)) - 1);
        if (wanted == 0) {
            return false;
        }
        if (!SwordService.payFor(player, state, MagicContent.THE_BEARING, -1)) {
            return false;
        }

        int pulled = 0;
        for (int slot = Math.min(array.size(), MASK_BITS) - 1; slot >= 0; slot--) {
            if ((wanted & (1 << slot)) != 0 && array.pull(slot)) {
                pulled++;
            }
        }
        SwordService.tendArrayEntity(player, state);
        state.sync(player);
        if (pulled > 0) {
            player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND,
                    SoundSource.PLAYERS, 0.35F, 1.7F);
        }
        return pulled > 0;
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
                        .spokes(12, 0.22F, true)
                        .core(CoreKind.IRIS, ColorRole.HOT).spin(SpinSignature.SLOW))
                .anchor(CircleAnchor.EYE_FORWARD)
                .silhouette(Silhouette.custom("the_bearing", 1.0F))
                .release(ReleaseMode.LIFT, ProfileCues.FirstPersonPreset.CASTER_LIGHT)
                .impact(FxKinds.Mark.CLOCK_SPOKES, FxKinds.Smoke.RUNE_MOTE, FxKinds.Overlay.HEX_PULSE)
                .budget(1)
                .bounds(1.5F, 1.5F, 1.5F);
    }
}
