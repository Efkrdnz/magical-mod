package com.efkrdnz.magical.client;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSchool;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.mana.ManaLedger;
import com.efkrdnz.magical.magic.mana.ManaLedgerService;
import com.efkrdnz.magical.magic.mana.WritAspect;
import com.efkrdnz.magical.magic.mana.WritOperation;
import com.efkrdnz.magical.magic.mana.WritSubject;
import com.efkrdnz.magical.network.MagicalNetwork;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * A page and three dials: the spell being legislated, then the aspect, the operation, and whose
 * magic it is about.
 *
 * <p>The page is deliberately not a fourth wheel. Manipulate Space is dials because its grammar is
 * a fixed dozen categories; a Ledger can hold four dozen spells and a book is a list, not a dial.
 * So the first thing the wielder does here is <em>turn a page</em>, and only what they have
 * actually witnessed is on it - an empty book opens to nothing and can declare nothing.
 *
 * <p>The dials are painted by {@link RuleWheelPainter}, shared with the space overlay rather than
 * copied.
 */
public final class WritOverlay {

    private static final int PAGE_COLOR = 0xFFE7A8;
    private static final int ASPECT_COLOR = 0xF0F4FF;
    private static final int OPERATION_COLOR = 0xC9D6FF;
    private static final int SUBJECT_COLOR = 0x9FB6FF;

    /** Where the wheels sit relative to the middle, so the page has room above them. */
    private static final int WHEEL_DROP = 16;

    private static boolean active;
    private static int focus;
    private static int selectedTarget;
    private static int selectedAspect;
    private static int selectedOperation;
    private static int selectedSubject;

    /** Wire forms, taken once when the hold begins: a skill id, or {@code #SCHOOL}. */
    private static List<String> targets = List.of();
    private static List<Component> targetLabels = List.of();
    private static List<Integer> targetColors = List.of();

    private WritOverlay() {}

    public static void begin() {
        active = true;
        focus = 0;
        selectedTarget = 0;
        selectedAspect = 0;
        selectedOperation = 0;
        selectedSubject = 0;
        readPage();
    }

    /** Releasing declares the writ. There is no cancel, the way the space dials have none. */
    public static void finish() {
        if (active && !targets.isEmpty()) {
            MagicalNetwork.sendWrit(targets.get(selectedTarget), selectedAspect, selectedOperation, selectedSubject);
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
        switch (focus) {
            case 0 -> {
                if (!targets.isEmpty()) {
                    selectedTarget = Math.floorMod(selectedTarget + step, targets.size());
                }
            }
            case 1 -> selectedAspect = Math.floorMod(selectedAspect + step, WritAspect.values().length);
            case 2 -> selectedOperation = Math.floorMod(selectedOperation + step, WritOperation.values().length);
            default -> selectedSubject = Math.floorMod(selectedSubject + step, WritSubject.values().length);
        }
        return true;
    }

    /** Left and right mouse walk the page and the three dials. */
    public static boolean handleMouseButton(int button, int action) {
        if (!active || action != org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            return false;
        }
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            focus = Math.floorMod(focus - 1, 4);
            return true;
        }
        if (button == org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            focus = Math.floorMod(focus + 1, 4);
            return true;
        }
        return false;
    }

    /**
     * What the wielder may legislate: every school they have seen a spell of, then every spell.
     *
     * <p>Schools come first because they are the escalation, and because a book with one fire spell
     * in it can already tax fire - the wielder should see that offer the moment it exists.
     */
    private static void readPage() {
        ManaLedger book = ClientMagicState.get().manaLedger();
        List<String> wire = new ArrayList<>();
        List<Component> labels = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        for (MagicSchool school : ManaLedgerService.witnessedSchools(book)) {
            wire.add("#" + school.name());
            labels.add(Component.translatable("writ.magical.whole_school",
                    Component.translatable("school.magical." + school.name().toLowerCase(java.util.Locale.ROOT))));
            colors.add(school.color());
        }
        for (ResourceLocation id : book.witnessed()) {
            MagicSkillDefinition definition = MagicContent.get(id);
            wire.add(id.toString());
            labels.add(Component.translatable("skill.magical." + id.getPath()));
            colors.add(definition == null ? PAGE_COLOR : definition.school().color());
        }
        targets = List.copyOf(wire);
        targetLabels = List.copyOf(labels);
        targetColors = List.copyOf(colors);
    }

    public static void render(GuiGraphics guiGraphics, Minecraft minecraft) {
        if (!active) {
            return;
        }
        guiGraphics.fill(0, 0, guiGraphics.guiWidth(), guiGraphics.guiHeight(), 0x76060810);
        int centerY = guiGraphics.guiHeight() / 2 + WHEEL_DROP;
        renderPage(guiGraphics, minecraft);
        int[] centers = RuleWheelPainter.wheelCenters(guiGraphics.guiWidth());
        RuleWheelPainter.drawWheel(guiGraphics, minecraft, centers[0], centerY, focus == 1,
                labels(WritAspect.values()), selectedAspect, ASPECT_COLOR, Component.translatable("writ.magical.wheel.aspect"));
        RuleWheelPainter.drawWheel(guiGraphics, minecraft, centers[1], centerY, focus == 2,
                labels(WritOperation.values()), selectedOperation, OPERATION_COLOR, Component.translatable("writ.magical.wheel.operation"));
        RuleWheelPainter.drawWheel(guiGraphics, minecraft, centers[2], centerY, focus == 3,
                labels(WritSubject.values()), selectedSubject, SUBJECT_COLOR, Component.translatable("writ.magical.wheel.subject"));
        // The sentence goes under the page, where the thing it describes is. The dial titles sit at
        // centerY - 125, so there is exactly one line of room between the page and them and the
        // hint cannot have it; the hint goes in the band under the dials instead, which is clear
        // down to the hotbar.
        guiGraphics.drawCenteredString(minecraft.font, currentReading(),
                guiGraphics.guiWidth() / 2, 57, 0xCBD9FF);
        guiGraphics.drawCenteredString(minecraft.font, Component.translatable("writ.magical.release_hint"),
                guiGraphics.guiWidth() / 2, guiGraphics.guiHeight() - 30, 0x7F93A8);
    }

    /** The open page: what is being legislated, with what came before and after it either side. */
    private static void renderPage(GuiGraphics guiGraphics, Minecraft minecraft) {
        int width = guiGraphics.guiWidth();
        int middle = width / 2;
        boolean focused = focus == 0;
        guiGraphics.fill(middle - 190, 10, middle + 190, 52, focused ? 0xC00B1422 : 0x8C0B1422);
        guiGraphics.fill(middle - 190, 10, middle + 190, 11, focused ? 0xFFFFE7A8 : 0x60FFE7A8);
        guiGraphics.fill(middle - 190, 51, middle + 190, 52, focused ? 0xFFFFE7A8 : 0x60FFE7A8);
        guiGraphics.drawCenteredString(minecraft.font, Component.translatable("writ.magical.page"),
                middle, 15, focused ? 0xFFE7A8 : 0x8C9BB0);
        if (targets.isEmpty()) {
            guiGraphics.drawCenteredString(minecraft.font, Component.translatable("writ.magical.page_empty"),
                    middle, 31, 0xE06A6A);
            return;
        }
        guiGraphics.drawCenteredString(minecraft.font, targetLabels.get(selectedTarget), middle, 29,
                targetColors.get(selectedTarget) | 0xFF000000);
        if (targets.size() > 1) {
            int previous = Math.floorMod(selectedTarget - 1, targets.size());
            int next = Math.floorMod(selectedTarget + 1, targets.size());
            guiGraphics.drawString(minecraft.font, targetLabels.get(previous), middle - 184, 29, 0x55708294, false);
            String after = targetLabels.get(next).getString();
            guiGraphics.drawString(minecraft.font, after, middle + 184 - minecraft.font.width(after), 29, 0x55708294, false);
        }
        guiGraphics.drawCenteredString(minecraft.font,
                Component.literal((selectedTarget + 1) + " / " + targets.size()), middle, 41, 0x7F93A8);
    }

    /** The writ as a sentence, so the wielder reads what they are about to declare. */
    private static Component currentReading() {
        Component target = targets.isEmpty()
                ? Component.translatable("writ.magical.page_empty")
                : targetLabels.get(selectedTarget);
        return Component.translatable("writ.magical.reading", target,
                Component.translatable(WritAspect.values()[selectedAspect].translationKey()),
                Component.translatable(WritOperation.values()[selectedOperation].translationKey()),
                Component.translatable(WritSubject.values()[selectedSubject].translationKey()));
    }

    private static List<Component> labels(Enum<?>[] values) {
        return Arrays.stream(values).<Component>map(value -> Component.translatable(translationKey(value))).toList();
    }

    private static String translationKey(Enum<?> value) {
        if (value instanceof WritAspect aspect) {
            return aspect.translationKey();
        }
        if (value instanceof WritOperation operation) {
            return operation.translationKey();
        }
        if (value instanceof WritSubject subject) {
            return subject.translationKey();
        }
        return value.name();
    }
}
