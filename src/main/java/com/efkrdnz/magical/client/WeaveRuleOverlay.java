package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.mana.WeaveAspect;
import com.efkrdnz.magical.magic.mana.WeaveOperation;
import com.efkrdnz.magical.magic.mana.WeaveSubject;
import com.efkrdnz.magical.network.MagicalNetwork;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Three wheels for one Weave rule: an aspect, an operation, and whose magic it is about.
 *
 * <p>Held open from a cast slot carrying Weave Rules, exactly as Manipulate Space opens its own.
 * Scroll turns the focused wheel, Tab moves between them, release writes the rule. Without this the
 * grammar existed only behind a command, which is to say it existed only for an operator - the
 * whole Authority is thirty-six choices and a player could not make any of them.
 *
 * <p>The painting is {@code SpaceManipulationOverlay}'s, reused rather than copied: same package,
 * same wheels, different vocabulary and a colder palette.
 */
public final class WeaveRuleOverlay {

    private static final int ASPECT_COLOR = 0xF0F4FF;
    private static final int OPERATION_COLOR = 0xC9D6FF;
    private static final int SUBJECT_COLOR = 0x9FB6FF;

    private static boolean active;
    private static int activeWheel;
    private static int selectedAspect;
    private static int selectedOperation;
    private static int selectedSubject;

    private WeaveRuleOverlay() {}

    public static void begin() {
        active = true;
        activeWheel = 0;
        selectedAspect = 0;
        selectedOperation = 0;
        selectedSubject = 0;
    }

    /** Releasing writes the rule. There is no cancel, the way the space wheels have none. */
    public static void finish() {
        if (active) {
            MagicalNetwork.sendWeaveRule(selectedAspect, selectedOperation, selectedSubject);
        }
        active = false;
    }

    public static boolean active() {
        return active;
    }

    public static boolean handleScroll(double delta) {
        if (!active || delta == 0.0D) {
            return active;
        }
        int step = delta > 0.0D ? -1 : 1;
        switch (activeWheel) {
            case 0 -> selectedAspect = Math.floorMod(selectedAspect + step, WeaveAspect.values().length);
            case 1 -> selectedOperation = Math.floorMod(selectedOperation + step, WeaveOperation.values().length);
            default -> selectedSubject = Math.floorMod(selectedSubject + step, WeaveSubject.values().length);
        }
        return true;
    }

    /** Left and right mouse walk the three wheels, as they do on the space wheels. */
    public static boolean handleMouseButton(int button, int action) {
        if (!active || action != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            return false;
        }
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            activeWheel = Math.floorMod(activeWheel - 1, 3);
            return true;
        }
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            activeWheel = Math.floorMod(activeWheel + 1, 3);
            return true;
        }
        return false;
    }

    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        if (!active) {
            return;
        }
        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), 0x76060810);
        int centerY = guiGraphics.guiHeight() / 2;
        int[] centers = SpaceManipulationOverlay.wheelCenters(guiGraphics.guiWidth());
        SpaceManipulationOverlay.drawWheel(guiGraphics, minecraft, centers[0], centerY, activeWheel == 0,
                aspectLabels(), selectedAspect, ASPECT_COLOR, Component.translatable("weave.magical.wheel.aspect"));
        SpaceManipulationOverlay.drawWheel(guiGraphics, minecraft, centers[1], centerY, activeWheel == 1,
                operationLabels(), selectedOperation, OPERATION_COLOR, Component.translatable("weave.magical.wheel.operation"));
        SpaceManipulationOverlay.drawWheel(guiGraphics, minecraft, centers[2], centerY, activeWheel == 2,
                subjectLabels(), selectedSubject, SUBJECT_COLOR, Component.translatable("weave.magical.wheel.subject"));
        guiGraphics.drawCenteredString(minecraft.font, Component.translatable("weave.magical.release_hint"),
                guiGraphics.guiWidth() / 2, centerY + 138, 0xE4ECFF);
        guiGraphics.drawCenteredString(minecraft.font, currentReading(),
                guiGraphics.guiWidth() / 2, centerY + 152, 0x9FB6FF);
    }

    /** The rule as a sentence, so the player reads what they are about to declare. */
    private static Component currentReading() {
        return Component.translatable("weave.magical.reading",
                Component.translatable(WeaveAspect.values()[selectedAspect].translationKey()),
                Component.translatable(WeaveOperation.values()[selectedOperation].translationKey()),
                Component.translatable(WeaveSubject.values()[selectedSubject].translationKey()));
    }

    private static List<Component> aspectLabels() {
        return Arrays.stream(WeaveAspect.values()).<Component>map(a -> Component.translatable(a.translationKey())).toList();
    }

    private static List<Component> operationLabels() {
        return Arrays.stream(WeaveOperation.values()).<Component>map(o -> Component.translatable(o.translationKey())).toList();
    }

    private static List<Component> subjectLabels() {
        return Arrays.stream(WeaveSubject.values()).<Component>map(s -> Component.translatable(s.translationKey())).toList();
    }
}
