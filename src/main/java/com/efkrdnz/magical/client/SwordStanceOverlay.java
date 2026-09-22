package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.sword.SwordRules;
import com.efkrdnz.magical.magic.sword.stance.Formation;
import com.efkrdnz.magical.magic.sword.stance.Slot;
import com.efkrdnz.magical.magic.sword.stance.SwordStance;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Six stances in a row, and each one draws its own formation.
 *
 * <p><b>The diagram is the real arithmetic.</b> Every cell runs {@code Formation.place} for the
 * wielder's own complement and flattens the result - x across, y up, the depth thrown away - so
 * the picture in the picker is the shape that will actually stand round them, and it cannot
 * drift from the code the way an artist's impression would. Guard is a wide low arc, Vanguard two
 * rings stacked, Crown a halo, Wings two swept fans, Coil a low ring, Rain a cluster overhead,
 * and those are not descriptions somebody typed: they are what the function returns.
 *
 * <p>Frameless, so a scrim and drop-shadowed text and nothing else - see
 * {@link SwordStanceLayout#SCRIM} for the contrast arithmetic that makes the scrim compulsory
 * rather than decorative. Three readings share the row and each owns a channel of its own, which
 * is the rule the loadout switcher settled: <em>which</em> stance is focused is brightness;
 * <em>which</em> is worn is hue, gold; and <em>whether</em> a release will be refused is the word
 * on the second line, which is reserved whether or not it is used so nothing below it moves when
 * a locked stance is walked onto.
 */
public final class SwordStanceOverlay {

    /** The steel's own colour, which is the school's. */
    private static final int STEEL = 0xB9C4CE;

    private static final int FOCUS_INK = 0xFFFFFF;
    private static final int REST_INK = 0x93A2AE;
    private static final int WORN_INK = 0xE8C77A;
    private static final int LOCKED_INK = 0x6A5C5C;
    private static final int DESC_INK = 0xC3D0DA;
    private static final int HINT_INK = 0x76848F;

    /** The wielder's own mark at the middle of a diagram: dim, because it is the reference. */
    private static final int BODY_INK = 0x93A7B8;

    /** Half the arm of that cross, in pixels. */
    private static final int BODY_MARK = 2;

    private static boolean active;
    private static int focus;

    private SwordStanceOverlay() {}

    /** Opens on whatever the wielder is already standing in, so a glance changes nothing. */
    public static void begin() {
        active = true;
        focus = ClientMagicState.get().swordArray().stance().ordinal();
    }

    /**
     * Releasing takes the focused stance.
     *
     * <p>A stance the rung has not opened is dropped here rather than sent and refused: the
     * server re-checks it anyway, but a packet per glance at a locked row is a packet per glance.
     */
    public static void finish() {
        if (active) {
            SwordStance wanted = SwordStance.byOrdinal(focus);
            MagicalNetwork.sendSetStance(rules().allows(wanted) ? wanted.ordinal() : -1);
        }
        active = false;
    }

    /**
     * Closes without choosing.
     *
     * <p>Used when a screen opens over the hold, the way the Causal Anchor's chooser is: a screen
     * opening is not the wielder letting go, and taking whatever the row happened to be resting
     * on would be the exact fumble a hold exists to prevent.
     */
    public static void cancel() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static boolean handleScroll(double delta) {
        if (!active || delta == 0.0D) {
            return active;
        }
        focus = Math.floorMod(focus + (delta > 0.0D ? -1 : 1), SwordStanceLayout.CELLS);
        return true;
    }

    /** Left and right mouse walk the row, the way they walk every other hold in the mod. */
    public static boolean handleMouse(int button, int action) {
        if (!active || action != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            return false;
        }
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            focus = Math.floorMod(focus - 1, SwordStanceLayout.CELLS);
            return true;
        }
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            focus = Math.floorMod(focus + 1, SwordStanceLayout.CELLS);
            return true;
        }
        return false;
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        if (!active || minecraft.font == null) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        graphics.fill(0, 0, width, height, SwordStanceLayout.SCRIM);

        SwordRules rules = rules();
        SwordStance worn = ClientMagicState.get().swordArray().stance();
        int swords = rules.swords();
        int limit = SwordStanceLayout.textLimit(width);

        for (int i = 0; i < SwordStanceLayout.CELLS; i++) {
            SwordStance stance = SwordStance.byOrdinal(i);
            boolean focused = i == focus;
            boolean locked = !rules.allows(stance);
            int centre = SwordStanceLayout.cellCentre(width, i);

            drawFormation(graphics, stance, swords, centre,
                    SwordStanceLayout.diagramCentreY(width, height),
                    SwordStanceLayout.diagramHalf(width), locked ? LOCKED_INK : STEEL,
                    focused ? 0xFF : 0x9A);

            int ink = locked ? LOCKED_INK : stance == worn ? WORN_INK : focused ? FOCUS_INK : REST_INK;
            graphics.drawCenteredString(minecraft.font,
                    minecraft.font.plainSubstrByWidth(
                            Component.translatable(stance.nameKey()).getString(), limit),
                    centre, SwordStanceLayout.nameTop(width, height), ink);
            // The second line is reserved whether or not it is used, so walking onto a locked
            // stance does not shift the row under the cursor.
            if (locked) {
                graphics.drawCenteredString(minecraft.font,
                        minecraft.font.plainSubstrByWidth(
                                Component.translatable("stance.magical.locked").getString(), limit),
                        centre, SwordStanceLayout.subTop(width, height), LOCKED_INK);
            } else if (stance == worn) {
                graphics.drawCenteredString(minecraft.font,
                        minecraft.font.plainSubstrByWidth(
                                Component.translatable("stance.magical.worn").getString(), limit),
                        centre, SwordStanceLayout.subTop(width, height), WORN_INK);
            }
        }

        SwordStance chosen = SwordStance.byOrdinal(focus);
        graphics.drawCenteredString(minecraft.font,
                minecraft.font.plainSubstrByWidth(
                        Component.translatable(chosen.descriptionKey()).getString(),
                        SwordStanceLayout.descLimit(width)),
                width / 2, SwordStanceLayout.descTop(width, height), DESC_INK);
        // Truncated like the rest of the row rather than trusted to fit: the loadout switcher's
        // caption was the one string on a frameless surface nobody clamped, and thirty-four
        // characters of it ran out through the panel it was drawn in and onto the sky.
        graphics.drawCenteredString(minecraft.font,
                minecraft.font.plainSubstrByWidth(
                        Component.translatable("stance.magical.release_hint").getString(),
                        SwordStanceLayout.descLimit(width)),
                width / 2, SwordStanceLayout.hintTop(width, height), HINT_INK);
    }

    /**
     * One stance's formation, flattened and scaled to fit the diagram square.
     *
     * <p>The depth is thrown away rather than projected, because a projection needs a camera and
     * the wielder is standing inside this shape - so there is no viewpoint that shows Guard's arc
     * and Vanguard's depth at once. Front-on is the honest choice: it is the view the wielder has
     * of their own formation, and the two stances that differ most in depth differ in the drawing
     * anyway, because Vanguard's two rings are drawn at different radii.
     *
     * <p>Scaled to whatever the largest offset is rather than to a constant, so a stance whose
     * blades sit close to the body fills its cell exactly as a wide one does and the six diagrams
     * read as six shapes rather than as one shape at six sizes.
     */
    private static void drawFormation(GuiGraphics graphics, SwordStance stance, int swords,
            int centreX, int centreY, int half, int rgb, int alpha) {
        int count = Math.max(1, swords);
        Slot[] slots = new Slot[count];
        double extent = 0.5D;
        for (int i = 0; i < count; i++) {
            slots[i] = Formation.place(stance, i, count, 0.0D);
            extent = Math.max(extent, Math.max(Math.abs(slots[i].x()), Math.abs(slots[i].y())));
        }
        double scale = half / extent;
        int colour = (alpha << 24) | (rgb & 0xFFFFFF);
        // The wielder, at the frame origin, and every diagram needs it. The scale is each
        // stance's own, so the marks alone say "some bars in a square" and nothing else: Crown's
        // ring and Coil's are the same ring drawn at the same size, and the only thing that tells
        // them apart is that one of them is above this cross and the other is around it.
        int body = (Math.min(alpha, 0xC8) << 24) | (BODY_INK & 0xFFFFFF);
        graphics.fill(centreX - BODY_MARK, centreY, centreX + BODY_MARK + 1, centreY + 1, body);
        graphics.fill(centreX, centreY - BODY_MARK, centreX + 1, centreY + BODY_MARK + 1, body);
        for (Slot slot : slots) {
            // +X is the wielder's left, and this is the view they have of their own formation -
            // so the screen's x runs the other way or the diagram is a mirror of the thing it is
            // a diagram of, which is the silent-mirror failure ArrayPose's class note warns about
            // wearing a different coat.
            int x = centreX - (int) Math.round(slot.x() * scale);
            int y = centreY - (int) Math.round(slot.y() * scale);
            graphics.fill(x, y - SwordStanceLayout.BLADE_HALF, x + 1,
                    y + SwordStanceLayout.BLADE_HALF, colour);
        }
    }

    private static SwordRules rules() {
        return ClientMagicState.get().swordArray().rules();
    }
}
