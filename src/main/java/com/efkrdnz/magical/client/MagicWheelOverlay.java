package com.efkrdnz.magical.client;

import com.efkrdnz.magical.client.hud.MagicalClientConfig;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicLoadout;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.network.MagicalNetwork;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The loadout switcher: hold the wheel key, scroll to a loadout, release to take it.
 *
 * <p>This was a radial of individual skills. Loadouts are named by the player, and a radial cannot
 * hold something like Defensive Rotation legibly at any radius, so it is a vertical column against
 * the left edge instead - names read straight and the list grows downward rather than crowding.
 *
 * <p>It is a <b>rail</b> now and not a panel. It was the last overlay in the mod still wearing a
 * frame, and the frame was also hiding a bug: the caption was drawn with no truncation at all, so
 * thirty-four characters of "Scroll to choose, release to switch" ran out through the right wall
 * of a 148px box and sat unbacked on the sky. The geometry moved to {@link LoadoutSwitcherLayout},
 * where a test can reach it, and nothing is drawn now without a measured width.
 *
 * <p><b>Frameless here means a scrim, not bare text</b>, and that distinction is the design. A
 * drop shadow preserves the shape of a glyph and does nothing for its contrast, so over noon
 * daylight sand the pale "this one is selected" ink is actually <i>fainter</i> than the grey it is
 * supposed to stand out from: the highlight inverts on bright ground and the list becomes a
 * riddle. Every other frameless surface in the mod dims the world first for exactly this reason
 * ({@code SpaceManipulationOverlay} at 0xA6, {@code FractureOverlay} at 0x76), so this one does
 * too, at a weight between them. The world goes dark; the rail is bright on it; there is still no
 * plate, no border and no box anywhere.
 *
 * <p>Three readings share the rail and none of them may be mistaken for another, so each gets its
 * own channel. Where the wheel is pointing is <b>position and brightness</b>: a mark in the gutter
 * and the only white text on screen. Which loadout is actually worn is <b>hue</b>: gold, with a
 * hairline under the name. Whether a release will do anything is the <b>accent colour</b> of the
 * mark plus a word on the caption line. Position, brightness and hue are independent, so all three
 * read at once - which they must, because {@code highlighted} opens on the active row.
 *
 * <p>The key is shared with the parry prompt, which always wins: {@link MagicalClientEvents} asks
 * {@link ClientCounterPrompt} first and only passes the key through when the counter did not
 * consume it. During a counter the window is a handful of ticks, and a list opening instead of a
 * parry would lose the exchange.
 */
public final class MagicWheelOverlay {
    private static final float FADE_STEP = 0.22F;
    private static final int ACCENT_OPEN = 0xFF5FD4FF;
    private static final int ACCENT_LOCKED = 0xFFE06470;
    private static final int ACCENT_ACTIVE = 0xFFF7D774;

    /**
     * The world, dimmed. Between the Manipulate Space selector at 0xA6 and the Fracture overlay at
     * 0x76: the switcher is a list you read rather than something you aim through, but it is still
     * a two-second hold and not an editor.
     */
    private static final int SCRIM = 0x8C060B14;

    /** The mark in the gutter that says which row a release would take. */
    private static final String MARK = ">";

    /**
     * A row you are not pointing at.
     *
     * <p>Not {@code TEXT_MUTED}, which is tuned for dark panel gradients and falls to about 1.4:1
     * on dimmed daylight ground - a ghost. This sits a clear step under the white of the
     * highlighted row, which is the only job the contrast between the two has.
     */
    private static final int INK_REST = 0xFFC2CCD9;

    /** An unbound slot: a dash, not a dark dot, because a dark dot on dark ground is nothing. */
    private static final int PIP_EMPTY = 0xFF99A6B8;

    /** How far the rail slides in from the left as it opens. */
    private static final int SLIDE = 5;

    private static boolean active;
    private static boolean keyWasDown;
    private static int highlighted;
    private static float fade;

    private MagicWheelOverlay() {}

    public static boolean isActive() {
        return active;
    }

    /**
     * Scroll moves the highlight while the switcher is open.
     *
     * @return true when the scroll was consumed, so the hotbar does not move as well
     */
    public static boolean handleScroll(double scrollDeltaY) {
        if (!active || scrollDeltaY == 0.0D) {
            return false;
        }
        int count = loadouts().size();
        if (count > 1) {
            // Scrolling up walks up the list, which is the opposite sign to the vanilla hotbar.
            highlighted = Math.floorMod(highlighted - (int) Math.signum(scrollDeltaY), count);
        }
        return true;
    }

    public static void tick(Minecraft minecraft, boolean keyDown) {
        if (minecraft.player == null || minecraft.screen != null) {
            active = false;
            fade = 0.0F;
            keyWasDown = keyDown;
            return;
        }
        if (keyDown && !keyWasDown) {
            active = true;
            highlighted = ClientMagicState.get().activeLoadoutIndex();
        } else if (!keyDown && keyWasDown && active) {
            commit();
        }
        fade = active ? Math.min(1.0F, fade + FADE_STEP) : Math.max(0.0F, fade - FADE_STEP);
        keyWasDown = keyDown;
    }

    /**
     * Take the highlighted loadout.
     *
     * <p>Every charge handler is cancelled first. Charge state is keyed by slot number, so a switch
     * mid-charge would otherwise leave a handler charging the skill that used to be on that key -
     * you would wind up one spell and release another.
     *
     * <p>The server decides whether the switch happens at all; it refuses one within a second of a
     * cast. This is a request, not the change itself.
     */
    private static void commit() {
        active = false;
        List<MagicLoadout> loadouts = loadouts();
        if (highlighted < 0 || highlighted >= loadouts.size()
                || highlighted == ClientMagicState.get().activeLoadoutIndex()) {
            return;
        }
        SovereignAegisInput.cancel();
        BlackFlamesInput.cancel();
        SpaceOffenseInput.cancel();
        SoulVowInput.cancel();
        MagicBarrageInput.cancel();
        SpaceAuthorityInput.cancel();
        BloodShapeInput.cancel();
        MagicalNetwork.sendSelectLoadout(highlighted);
    }

    private static List<MagicLoadout> loadouts() {
        return ClientMagicState.get().loadouts();
    }

    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        if (fade <= 0.01F || minecraft.player == null) {
            return;
        }
        List<MagicLoadout> loadouts = loadouts();
        if (loadouts.isEmpty()) {
            return;
        }
        int guiWidth = guiGraphics.guiWidth();
        int guiHeight = guiGraphics.guiHeight();
        int count = loadouts.size();
        int alpha = (int) (fade * 255.0F);

        // The lock is predicted here rather than streamed: the server sent its value with the sync
        // the cast already triggered, and it counts down locally instead of costing a packet a tick.
        boolean locked = ClientMagicState.get().loadoutSwapLockTicks() > 0;
        int accent = locked ? ACCENT_LOCKED : ACCENT_OPEN;

        guiGraphics.fill(0, 0, guiWidth, guiHeight,
                MagicalGuiStyle.withAlpha(SCRIM, (int) (fade * (SCRIM >>> 24))));

        // The rail arrives from the left rather than only fading up. With no plate under it, alpha
        // alone reads as the terrain showing through the letters instead of as the list appearing.
        int slide = MagicalClientConfig.current().reducedMotion()
                ? 0
                : Math.round(SLIDE * (1.0F - easeOut(fade)));

        int activeIndex = ClientMagicState.get().activeLoadoutIndex();
        for (int index = 0; index < count; index++) {
            MagicLoadout loadout = loadouts.get(index);
            boolean chosen = index == highlighted;
            boolean worn = index == activeIndex;

            if (chosen) {
                Rect mark = LoadoutSwitcherLayout.mark(index, count, guiHeight);
                guiGraphics.drawString(minecraft.font, MARK, mark.x() - slide, mark.y(),
                        MagicalGuiStyle.withAlpha(accent, alpha), true);
            }

            drawPips(guiGraphics, loadout, index, count, guiWidth, guiHeight, slide, alpha);

            String name = fit(minecraft, loadout.name(), guiWidth);
            int measured = minecraft.font.width(name);
            Rect row = LoadoutSwitcherLayout.name(index, measured, count, guiWidth, guiHeight);
            guiGraphics.drawString(minecraft.font, name, row.x() - slide, row.y(),
                    MagicalGuiStyle.withAlpha(ink(chosen, worn), alpha), true);

            if (worn) {
                Rect rule = LoadoutSwitcherLayout.activeRule(index, measured, count, guiWidth, guiHeight);
                guiGraphics.fill(rule.x() - slide, rule.y(), rule.right() - slide, rule.bottom(),
                        MagicalGuiStyle.withAlpha(ACCENT_ACTIVE, alpha));
            }
        }

        // The caption line is reserved whether or not anything is on it: the cast keys still fire
        // while the switcher is held, so a lock can land mid-list, and a list that jumps half a
        // line at that moment is a list you lose your place in.
        if (locked) {
            Rect caption = LoadoutSwitcherLayout.caption(count, guiWidth, guiHeight);
            String text = fit(minecraft,
                    Component.translatable("screen.magical.loadout_locked").getString(), guiWidth);
            guiGraphics.drawString(minecraft.font, text, caption.x() - slide, caption.y(),
                    MagicalGuiStyle.withAlpha(ACCENT_LOCKED, alpha), true);
        }
    }

    /**
     * Brightness for where the wheel is, hue for what is worn, and both at once on the row that is
     * both - which is the row the list opens on, so it is the common case and not the corner one.
     */
    private static int ink(boolean chosen, boolean worn) {
        if (worn) {
            return chosen ? MagicalGuiStyle.brighten(ACCENT_ACTIVE, 1.18F) : ACCENT_ACTIVE;
        }
        return chosen ? 0xFF000000 | MagicalGuiStyle.TEXT_PRIMARY : INK_REST;
    }

    /**
     * A string cut to what the rail allows on this screen, with dots to say it was cut.
     *
     * <p>Never wrapped, because a second line would land on the row below, and never scaled,
     * because a second text size in a two-second glance is noise. The dots are three periods
     * rather than an ellipsis so the glyph comes off the font page every resource pack has.
     */
    private static String fit(Minecraft minecraft, String text, int guiWidth) {
        int limit = LoadoutSwitcherLayout.textLimit(guiWidth);
        if (minecraft.font.width(text) <= limit) {
            return text;
        }
        String dots = "...";
        return minecraft.font.plainSubstrByWidth(text, limit - minecraft.font.width(dots)) + dots;
    }

    /** Settles without overshooting, the curve the other hold overlays open on. */
    private static float easeOut(float t) {
        float clamped = Math.max(0.0F, Math.min(1.0F, t));
        return 1.0F - (1.0F - clamped) * (1.0F - clamped);
    }

    /**
     * Four colour dots per row, one per slot, leading the row rather than trailing it.
     *
     * <p>They make a loadout recognisable without reading it, which is the point of holding the
     * key rather than cycling blind. They lead now because a trailing run needs a right edge to
     * hang off and the right edge was the frame; as a leading run they also line up into one
     * colour grid down the rail, so the shape of a loadout is legible before any word is.
     *
     * <p>An empty slot is a dash across the middle of the dot rather than a dark dot. It differs
     * by shape as well as by tone, so a half-built loadout is visibly half-built even where the
     * ground behind it defeats the tone.
     */
    private static void drawPips(GuiGraphics guiGraphics, MagicLoadout loadout, int row, int count,
            int guiWidth, int guiHeight, int slide, int alpha) {
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            ResourceLocation id = loadout.slot(slot);
            MagicSkillDefinition skill = id == null ? null : MagicContent.get(id);
            Rect pip = LoadoutSwitcherLayout.pip(row, slot, count, guiWidth, guiHeight);
            int x = pip.x() - slide;
            if (skill == null) {
                int y = pip.y() + pip.h() / 2;
                guiGraphics.fill(x, y, x + pip.w(), y + 1, MagicalGuiStyle.withAlpha(PIP_EMPTY, alpha));
            } else {
                guiGraphics.fill(x, pip.y(), x + pip.w(), pip.bottom(),
                        MagicalGuiStyle.withAlpha(0xFF000000 | skill.color(), alpha));
            }
        }
    }
}
