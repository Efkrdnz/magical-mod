package com.efkrdnz.magical.client.hud;

import com.efkrdnz.magical.client.FirstPersonEffects;
import com.efkrdnz.magical.client.hud.HudSnapshot.Label;
import com.efkrdnz.magical.magic.SpaceRuleCategory;
import com.efkrdnz.magical.magic.SpaceRuleChange;
import com.efkrdnz.magical.magic.SpaceRuleNotation;
import com.efkrdnz.magical.magic.SpaceRuleNotation.Formula;
import com.efkrdnz.magical.magic.SpaceRuleOperation;
import com.efkrdnz.magical.magic.SpaceTargetGroup;
import com.efkrdnz.magical.magic.visual.FxKinds;
import com.efkrdnz.magical.network.FirstPersonEffectPayload;
import com.efkrdnz.magical.registry.MagicalSounds;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The rule flash: the formula that pops above the crosshair when the server says a Manipulate
 * Space rule landed, with the symbol the rule bends marked by the kind of change.
 *
 * <p>One shot at a time. {@link #begin} shapes its five strings once, plays the stamp and the
 * kind's cue, pushes the kind's wash into the first-person overlay, and replaces whatever was
 * running; the tick retires it at the end of its life. The renderer reads {@link #current()} and
 * the clock and nothing else, so a frame without a flash costs nothing.
 */
public final class RuleFlash {

    /** Everything the renderer needs, shaped once when the rule lands. */
    public record Shot(SpaceRuleChange change, int variant, int tint, Label before, Label symbol, Label after,
            Label zero, Label caption, long startTick, int lifetime, int seed) {

        /** The lifetime fraction at {@code now}: 0 as it pops, 1 when it is gone. */
        public float phase(float now) {
            float phase = (now - startTick) / lifetime;
            return phase < 0.0F ? 0.0F : Math.min(1.0F, phase);
        }
    }

    public static final int LIFETIME_TICKS = 48;
    public static final int REDUCED_LIFETIME_TICKS = 32;
    /** The kind's cue follows the shared stamp by this much, so the two read as one sound. */
    private static final int CUE_DELAY_TICKS = 3;
    private static final float STAMP_VOLUME = 0.5F;
    private static final float CUE_VOLUME = 0.6F;
    /** Seeds are six bits on the wire to the shader. */
    private static final int SEED_RANGE = 64;

    private static final Random SEEDS = new Random();
    private static Shot current;

    private RuleFlash() {}

    /** A rule landed: shape the formula, sound it, wash the screen, and show it from {@code now}. */
    public static void begin(SpaceRuleCategory category, SpaceRuleOperation operation, SpaceTargetGroup target,
            long now, Font font, HudOptions options) {
        SpaceRuleChange change = SpaceRuleNotation.change(operation);
        Formula formula = SpaceRuleNotation.formula(category);
        int tint = HudPalette.change(change);
        Component caption = Component.translatable("hud.magical.rule.caption",
                Component.translatable(category.translationKey()),
                Component.translatable(operation.translationKey()),
                Component.translatable(target.translationKey()));
        show(new Shot(change, SpaceRuleNotation.variant(operation), tint,
                label(font, Component.literal(formula.before()), HudPalette.TEXT_PRIMARY),
                label(font, Component.literal(formula.symbol()), tint),
                label(font, Component.literal(formula.after()), HudPalette.TEXT_PRIMARY),
                label(font, Component.literal("0"), tint),
                label(font, caption, HudPalette.TEXT_MUTED),
                now, options.reducedMotion() ? REDUCED_LIFETIME_TICKS : LIFETIME_TICKS, SEEDS.nextInt(SEED_RANGE)));
        play(change);
        if (!options.reducedMotion()) {
            wash(change, tint);
        }
    }

    /** Puts a shot on screen in place of whatever was there. */
    static void show(Shot shot) {
        current = shot;
    }

    /** Once a tick: the shot is gone at the end of its life. */
    public static void tick(long now) {
        if (current != null && now - current.startTick() >= current.lifetime()) {
            current = null;
        }
    }

    /** The flash on screen, or null. */
    public static Shot current() {
        return current;
    }

    public static void reset() {
        current = null;
    }

    private static Label label(Font font, Component text, int color) {
        FormattedCharSequence shaped = text.getVisualOrderText();
        return new Label(shaped, font.width(shaped), color);
    }

    private static void play(SpaceRuleChange change) {
        SoundManager sounds = Minecraft.getInstance().getSoundManager();
        sounds.play(SimpleSoundInstance.forUI(MagicalSounds.RULE_STAMP.get(), 1.0F, STAMP_VOLUME));
        sounds.playDelayed(SimpleSoundInstance.forUI(MagicalSounds.cue(change).get(), 1.0F, CUE_VOLUME), CUE_DELAY_TICKS);
    }

    /** The kind's full-screen wash, through the same overlay the hit effects use. Faint on purpose. */
    private static void wash(SpaceRuleChange change, int tint) {
        FirstPersonEffectPayload payload = switch (change) {
            case RAISE -> overlay(tint, FxKinds.Overlay.BLOOM_RAYS, 40, 12, 0.22F);
            case LOWER -> overlay(tint, FxKinds.Overlay.VIGNETTE, 63, 12, 0.28F);
            case ZERO -> overlay(tint, FxKinds.Overlay.STATIC_GLITCH, 30, 8, 0.18F);
            case FLIP -> overlay(tint, FxKinds.Overlay.PRISM_RING, 40, 12, 0.25F);
            case LOCK -> overlay(tint, FxKinds.Overlay.HEX_PULSE, 35, 12, 0.20F);
            // The one kind that also moves the camera: a short shake and a kick of field of view.
            case SURGE -> new FirstPersonEffectPayload(tint, 14, 0.35F, 6, 0.6F, 0, 4.0F)
                    .withOverlay(FxKinds.Overlay.SHOCK_RING.id(), 63, FirstPersonEffectPayload.OMNI);
            case AIM -> overlay(tint, FxKinds.Overlay.TUNNEL, 20, 10, 0.15F);
            case RESTORE -> overlay(0xFFFFFF, FxKinds.Overlay.FLASH, 20, 6, 0.10F);
        };
        FirstPersonEffects.apply(payload);
    }

    private static FirstPersonEffectPayload overlay(int color, FxKinds.Overlay kind, int strength, int ticks, float alpha) {
        return new FirstPersonEffectPayload(color, ticks, alpha, 0, 0.0F, 0, 0.0F)
                .withOverlay(kind.id(), strength, FirstPersonEffectPayload.OMNI);
    }
}
