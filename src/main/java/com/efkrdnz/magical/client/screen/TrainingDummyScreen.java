package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.entity.TrainingDummyEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.menu.TrainingDummyMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The dummy's rotation and settings.
 *
 * <p>Two lists on the left - every skill in the game, sub-skills included, and the passives - with
 * the controls on the right. Nothing here holds state of its own beyond which tab and how far the
 * list is scrolled: what is ticked comes off the dummy every frame, so the screen cannot drift out
 * of step with the entity it is configuring.
 */
public final class TrainingDummyScreen extends AbstractContainerScreen<TrainingDummyMenu> {

    public static final int ROW_HEIGHT = 16;
    public static final int ROWS = 10;
    private static final int LIST_WIDTH = 198;
    private static final int COLUMN_WIDTH = 132;
    private static final int TAB_WIDTH = 60;
    private static final int TAB_HEIGHT = 14;
    public static final int BUTTON_HEIGHT = 14;
    /**
     * Vertical layout, as offsets from the panel top.
     *
     * <p>Public and named rather than inlined into the geometry helpers so a test can do
     * the overlap arithmetic on the same numbers the renderer draws with. The list length is a
     * content count times a row height; nothing in the type system stops it growing into the
     * controls below it.
     */
    public static final int HEIGHT = 212;
    public static final int TAB_TOP = 24;
    public static final int LIST_TOP = 44;
    public static final int SLIDER_TOP = 70;
    public static final int CLEAR_TOP = 86;
    public static final int PARRY_TOP = 118;
    public static final int QTE_TOP = 134;
    public static final int RESET_TOP = 166;
    public static final int REMOVE_TOP = 184;

    private static final int TAB_IDLE = 0xFF243044;
    private static final int BUTTON_BASE = 0xFF2C3A50;
    private static final int BUTTON_DANGER = 0xFF5A2C36;

    /** One row: identity for the click, and the label and colour for the draw. */
    private record Entry(ResourceLocation id, Component name, int color) {}

    private final List<Entry> entries = new ArrayList<>();
    private boolean passiveTab;
    private int scroll;
    private boolean draggingDelay;

    public TrainingDummyScreen(TrainingDummyMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 360;
        imageHeight = HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        // Slotless: the inherited labels would print over the panel.
        titleLabelY = 10000;
        inventoryLabelY = 10000;
        rebuild();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * Rebuild the visible list for the current tab.
     *
     * <p>Entry order is the option order the menu publishes, so a row index is a button id without
     * any further translation - which is the only reason clicks can be one int.
     */
    private void rebuild() {
        entries.clear();
        if (passiveTab) {
            for (MagicPassiveDefinition passive : TrainingDummyMenu.passiveOptions()) {
                entries.add(new Entry(passive.id(), Component.translatable(passive.nameKey()),
                        0xFF000000 | passive.color()));
            }
        } else {
            for (ResourceLocation id : TrainingDummyMenu.skillOptions()) {
                MagicSkillDefinition skill = MagicContent.get(id);
                Component name = skill == null
                        ? Component.literal(id.getPath())
                        : Component.translatable(skill.nameKey());
                entries.add(new Entry(id, name, 0xFF000000 | (skill == null ? 0x8292AB : skill.color())));
            }
        }
        scroll = 0;
    }

    private boolean selected(Entry entry) {
        TrainingDummyEntity dummy = menu.dummy();
        if (dummy == null) {
            return false;
        }
        return passiveTab ? dummy.passives().contains(entry.id()) : dummy.skills().contains(entry.id());
    }

    // ---- geometry ----

    private int listX() { return leftPos + 8; }
    private int listY() { return topPos + LIST_TOP; }
    private int listBottom() { return listY() + ROWS * ROW_HEIGHT; }
    private int columnX() { return leftPos + 220; }
    private int tabY() { return topPos + TAB_TOP; }
    private int sliderY() { return topPos + SLIDER_TOP; }
    private int clearY() { return topPos + CLEAR_TOP; }
    private int parryY() { return topPos + PARRY_TOP; }
    private int qteY() { return topPos + QTE_TOP; }
    private int resetY() { return topPos + RESET_TOP; }
    private int removeY() { return topPos + REMOVE_TOP; }

    private int maxScroll() {
        return Math.max(0, entries.size() - ROWS);
    }

    private static boolean within(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    // ---- rendering ----

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        MagicalGuiStyle.screenBackground(g, left, top, left + imageWidth, top + imageHeight);
        MagicalGuiStyle.sectionLabel(g, font, left + 12, top + 10,
                Component.translatable("screen.magical.training_dummy_title"), MagicalGuiStyle.TEXT_PRIMARY);
        drawTabs(g);
        drawList(g, mouseX, mouseY);
        drawColumn(g);
    }

    private void drawTabs(GuiGraphics g) {
        MagicalGuiStyle.button(g, font, listX(), tabY(), TAB_WIDTH, TAB_HEIGHT,
                passiveTab ? TAB_IDLE : MagicalGuiStyle.ACCENT_ARCANE,
                Component.translatable("screen.magical.training_dummy_skills"));
        MagicalGuiStyle.button(g, font, listX() + TAB_WIDTH + 4, tabY(), TAB_WIDTH, TAB_HEIGHT,
                passiveTab ? MagicalGuiStyle.ACCENT_ARCANE : TAB_IDLE,
                Component.translatable("screen.magical.training_dummy_passives"));
        TrainingDummyEntity dummy = menu.dummy();
        int chosen = dummy == null ? 0 : (passiveTab ? dummy.passives().size() : dummy.skills().size());
        g.drawString(font, Component.translatable("screen.magical.training_dummy_chosen", chosen),
                listX() + TAB_WIDTH * 2 + 14, tabY() + 3, MagicalGuiStyle.TEXT_MUTED, false);
    }

    private void drawList(GuiGraphics g, int mouseX, int mouseY) {
        MagicalGuiStyle.inset(g, listX() - 2, listY() - 2, listX() + LIST_WIDTH + 2, listBottom() + 2);
        for (int row = 0; row < ROWS; row++) {
            int index = scroll + row;
            if (index >= entries.size()) {
                break;
            }
            Entry entry = entries.get(index);
            int y = listY() + row * ROW_HEIGHT;
            boolean ticked = selected(entry);
            boolean hovered = within(mouseX, mouseY, listX(), y, LIST_WIDTH, ROW_HEIGHT);
            MagicalGuiStyle.listRow(g, listX(), y, LIST_WIDTH, ROW_HEIGHT - 1, ticked || hovered, entry.color());
            MagicalGuiStyle.checkbox(g, listX() + 5, y + 2, ticked, entry.color());
            g.drawString(font, font.plainSubstrByWidth(entry.name().getString(), LIST_WIDTH - 26),
                    listX() + 21, y + 4,
                    ticked ? MagicalGuiStyle.TEXT_PRIMARY : MagicalGuiStyle.TEXT_MUTED, false);
        }
        MagicalGuiStyle.scrollbar(g, listX() + LIST_WIDTH + 6, listY(), ROWS * ROW_HEIGHT,
                Math.max(entries.size(), ROWS), ROWS, scroll);
    }

    private void drawColumn(GuiGraphics g) {
        TrainingDummyEntity dummy = menu.dummy();
        int delay = dummy == null ? TrainingDummyEntity.DEFAULT_DELAY : dummy.delayTicks();
        MagicalGuiStyle.sectionLabel(g, font, columnX(), topPos + TAB_TOP + 6,
                Component.translatable("screen.magical.training_dummy_rotation"), MagicalGuiStyle.ACCENT_GOLD);
        g.drawString(font, Component.translatable("screen.magical.training_dummy_delay",
                        String.format("%.2f", delay / 20.0F), delay),
                columnX(), sliderY() - 12, MagicalGuiStyle.TEXT_MUTED, false);
        MagicalGuiStyle.slider(g, columnX(), sliderY(), COLUMN_WIDTH, 8, fractionOf(delay),
                MagicalGuiStyle.ACCENT_GOLD);
        MagicalGuiStyle.button(g, font, columnX(), clearY(), COLUMN_WIDTH, BUTTON_HEIGHT, BUTTON_BASE,
                Component.translatable("screen.magical.training_dummy_clear"));

        MagicalGuiStyle.sectionLabel(g, font, columnX(), topPos + PARRY_TOP - 14,
                Component.translatable("screen.magical.training_dummy_settings"), MagicalGuiStyle.ACCENT_VIOLET);
        boolean parry = dummy != null && dummy.parryIncoming();
        boolean qte = dummy != null && dummy.alwaysQte();
        MagicalGuiStyle.checkbox(g, columnX(), parryY(), parry, MagicalGuiStyle.ACCENT_ARCANE);
        g.drawString(font, Component.translatable("screen.magical.training_dummy_parry"),
                columnX() + 16, parryY() + 2,
                parry ? MagicalGuiStyle.TEXT_PRIMARY : MagicalGuiStyle.TEXT_MUTED, false);
        MagicalGuiStyle.checkbox(g, columnX(), qteY(), qte, MagicalGuiStyle.ACCENT_VIOLET);
        g.drawString(font, Component.translatable("screen.magical.training_dummy_qte"),
                columnX() + 16, qteY() + 2,
                qte ? MagicalGuiStyle.TEXT_PRIMARY : MagicalGuiStyle.TEXT_MUTED, false);

        MagicalGuiStyle.sectionLabel(g, font, columnX(), topPos + RESET_TOP - 14,
                Component.translatable("screen.magical.training_dummy_meter"), MagicalGuiStyle.ACCENT_NATURE);
        MagicalGuiStyle.button(g, font, columnX(), resetY(), COLUMN_WIDTH, BUTTON_HEIGHT, BUTTON_BASE,
                Component.translatable("screen.magical.training_dummy_reset"));
        MagicalGuiStyle.button(g, font, columnX(), removeY(), COLUMN_WIDTH, BUTTON_HEIGHT, BUTTON_DANGER,
                Component.translatable("screen.magical.training_dummy_remove"));
    }

    // ---- delay arithmetic ----

    private static float fractionOf(int ticks) {
        return (float) (ticks - TrainingDummyEntity.MIN_DELAY)
                / (TrainingDummyEntity.MAX_DELAY - TrainingDummyEntity.MIN_DELAY);
    }

    /** Mouse x to ticks, matching the knob the slider draws at that fraction. */
    private int ticksAt(double mouseX) {
        float usable = COLUMN_WIDTH - MagicalGuiStyle.SLIDER_KNOB_WIDTH;
        float fraction = Mth.clamp((float) (mouseX - columnX()) / usable, 0.0F, 1.0F);
        return TrainingDummyEntity.MIN_DELAY + Math.round(
                fraction * (TrainingDummyEntity.MAX_DELAY - TrainingDummyEntity.MIN_DELAY));
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (within(mouseX, mouseY, listX(), tabY(), TAB_WIDTH, TAB_HEIGHT) && passiveTab) {
                passiveTab = false;
                rebuild();
                return true;
            }
            if (within(mouseX, mouseY, listX() + TAB_WIDTH + 4, tabY(), TAB_WIDTH, TAB_HEIGHT) && !passiveTab) {
                passiveTab = true;
                rebuild();
                return true;
            }
            if (within(mouseX, mouseY, listX(), listY(), LIST_WIDTH, ROWS * ROW_HEIGHT)) {
                int index = scroll + (int) ((mouseY - listY()) / ROW_HEIGHT);
                if (index >= 0 && index < entries.size()) {
                    press((passiveTab ? TrainingDummyMenu.BUTTON_PASSIVE_BASE
                            : TrainingDummyMenu.BUTTON_SKILL_BASE) + index);
                }
                return true;
            }
            if (within(mouseX, mouseY, columnX(), sliderY() - 4, COLUMN_WIDTH, 16)) {
                draggingDelay = true;
                press(TrainingDummyMenu.BUTTON_DELAY_BASE + ticksAt(mouseX));
                return true;
            }
            if (within(mouseX, mouseY, columnX(), clearY(), COLUMN_WIDTH, BUTTON_HEIGHT)) {
                press(TrainingDummyMenu.BUTTON_CLEAR_SKILLS);
                return true;
            }
            // The checkbox is 10px, which is a small target for a setting people toggle often, so
            // the whole labelled row takes the click.
            if (within(mouseX, mouseY, columnX(), parryY(), COLUMN_WIDTH, 12)) {
                press(TrainingDummyMenu.BUTTON_TOGGLE_PARRY);
                return true;
            }
            if (within(mouseX, mouseY, columnX(), qteY(), COLUMN_WIDTH, 12)) {
                press(TrainingDummyMenu.BUTTON_TOGGLE_QTE);
                return true;
            }
            if (within(mouseX, mouseY, columnX(), resetY(), COLUMN_WIDTH, BUTTON_HEIGHT)) {
                press(TrainingDummyMenu.BUTTON_RESET_METER);
                return true;
            }
            if (within(mouseX, mouseY, columnX(), removeY(), COLUMN_WIDTH, BUTTON_HEIGHT)) {
                press(TrainingDummyMenu.BUTTON_REMOVE);
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingDelay && button == 0) {
            press(TrainingDummyMenu.BUTTON_DELAY_BASE + ticksAt(mouseX));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingDelay = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (within(mouseX, mouseY, listX(), listY(), LIST_WIDTH + 12, ROWS * ROW_HEIGHT)) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * 2, 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }
}
