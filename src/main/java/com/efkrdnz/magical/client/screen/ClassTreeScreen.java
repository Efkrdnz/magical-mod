package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.classes.ClassTreeLayout;
import com.efkrdnz.magical.classes.MagicalClassDefinition;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.menu.ClassTreeMenu;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

/**
 * The radial class evolution tree: every base class sits near the centre and its branches fan
 * outward, one ring per tier. Drag to pan, scroll to zoom, hover for a tooltip. Owned nodes and
 * the edges between them are lit; affordable nodes glow on their accent; the rest are dimmed.
 */
public final class ClassTreeScreen extends AbstractContainerScreen<ClassTreeMenu> {
    private static final int NODE_W = (int) ClassTreeLayout.NODE_WIDTH;
    private static final int NODE_H = (int) ClassTreeLayout.NODE_HEIGHT;
    /** Low enough to fit the whole graph on screen at once, high enough to read a single branch. */
    private static final float MIN_ZOOM = 0.28F;
    private static final float MAX_ZOOM = 1.4F;
    private static final int[] ACCENTS = {
            MagicalGuiStyle.ACCENT_GOLD,
            MagicalGuiStyle.ACCENT_BLOOD,
            MagicalGuiStyle.ACCENT_NATURE,
            MagicalGuiStyle.ACCENT_ARCANE,
            MagicalGuiStyle.ACCENT_VIOLET};

    private float panX;
    private float panY;
    private float zoom = -1.0F;
    private ResourceLocation hovered;

    public ClassTreeScreen(ClassTreeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 420;
        imageHeight = 320;
    }

    @Override
    protected void init() {
        // A graph needs room, so this screen takes as much of the window as it can rather than
        // sitting in a fixed panel like the codex (444x340). Sizing must happen before
        // super.init(), which derives leftPos/topPos from these.
        imageWidth = Math.min(width - 12, 900);
        imageHeight = Math.min(height - 12, 600);
        super.init();
        titleLabelY = 10000;
        inventoryLabelY = 10000;
        if (zoom < 0.0F) {
            zoom = fitZoom();
        }
    }

    /** Zoom that shows the entire graph inside the viewport, so nothing starts off-screen. */
    private float fitZoom() {
        float span = (ClassTreeLayout.extent() + ClassTreeLayout.NODE_WIDTH) * 2.0F;
        float byWidth = (imageWidth - 24) / span;
        float byHeight = (imageHeight - 70) / span;
        return Mth.clamp(Math.min(byWidth, byHeight), MIN_ZOOM, MAX_ZOOM);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- geometry ----

    private int centreX() {
        return leftPos + imageWidth / 2;
    }

    private int centreY() {
        return topPos + imageHeight / 2 + 6;
    }

    private int screenX(ClassTreeLayout.Node node) {
        return Math.round(centreX() + (node.x() + panX) * zoom);
    }

    private int screenY(ClassTreeLayout.Node node) {
        return Math.round(centreY() + (node.y() + panY) * zoom);
    }

    private int nodeWidth() {
        return Math.max(24, Math.round(NODE_W * zoom));
    }

    private int nodeHeight() {
        return Math.max(10, Math.round(NODE_H * zoom));
    }

    // ---- rendering ----

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        PlayerMagicState state = ClientMagicState.get();
        int left = leftPos;
        int top = topPos;
        MagicalGuiStyle.screenBackground(guiGraphics, left, top, left + imageWidth, top + imageHeight);
        MagicalGuiStyle.sectionLabel(guiGraphics, font, left + 12, top + 10,
                Component.translatable("screen.magical.class_tree_title"), MagicalGuiStyle.TEXT_PRIMARY);

        hovered = null;
        // Clip the graph so panned nodes never spill over the frame or the footer.
        int clipTop = top + 26;
        int clipBottom = top + imageHeight - 26;
        guiGraphics.enableScissor(left + 4, clipTop, left + imageWidth - 4, clipBottom);
        drawEdges(guiGraphics, state);
        drawNodes(guiGraphics, state, mouseX, mouseY, clipTop, clipBottom);
        guiGraphics.disableScissor();

        drawFooter(guiGraphics, state, left, top);
        // The tooltip is deliberately NOT drawn here: node labels go through the deferred font
        // batch and would flush on top of it. It is drawn in render(), raised on z.
    }

    private void drawEdges(GuiGraphics guiGraphics, PlayerMagicState state) {
        // Two passes so a walked route is never buried under the dim lattice behind it.
        drawEdgePass(guiGraphics, state, false);
        drawEdgePass(guiGraphics, state, true);
    }

    private void drawEdgePass(GuiGraphics guiGraphics, PlayerMagicState state, boolean walkedPass) {
        for (MagicalClassDefinition definition : MagicalClasses.all()) {
            ClassTreeLayout.Node child = ClassTreeLayout.get(definition.id());
            if (child == null) {
                continue;
            }
            boolean childOwned = state.hasClass(definition.id());
            for (ResourceLocation parentId : definition.parents()) {
                ClassTreeLayout.Node parent = ClassTreeLayout.get(parentId);
                if (parent == null) {
                    continue;
                }
                boolean parentOwned = state.hasClass(parentId);
                boolean walked = childOwned && parentOwned;
                if (walked != walkedPass) {
                    continue;
                }
                int colour;
                int thickness;
                if (walked) {
                    colour = MagicalGuiStyle.withAlpha(accentFor(definition.id()), 0xFF);
                    thickness = 3;
                } else if (parentOwned) {
                    // Reachable next step: bright enough to trace, dimmer than a walked route.
                    colour = MagicalGuiStyle.withAlpha(accentFor(definition.id()), 0xAA);
                    thickness = 2;
                } else {
                    colour = 0xCC46577A;
                    thickness = 2;
                }
                line(guiGraphics, screenX(parent), screenY(parent), screenX(child), screenY(child), colour, thickness);
            }
        }
    }

    /**
     * Integer line as a run of small squares. GuiGraphics has no line primitive and the codex never
     * needed one; this runs for roughly sixty edges on a static screen, so the cost is fine.
     */
    private void line(GuiGraphics guiGraphics, int x0, int y0, int x1, int y1, int colour, int thickness) {
        int dx = x1 - x0;
        int dy = y1 - y0;
        int steps = Math.max(Math.abs(dx), Math.abs(dy));
        if (steps <= 0) {
            return;
        }
        int half = thickness / 2;
        // Step by half the thickness: the squares still overlap into a solid line at a fraction
        // of the draw calls a per-pixel walk would need.
        int stride = Math.max(1, thickness - 1);
        for (int i = 0; i <= steps; i += stride) {
            int x = x0 + dx * i / steps;
            int y = y0 + dy * i / steps;
            guiGraphics.fill(x - half, y - half, x - half + thickness, y - half + thickness, colour);
        }
    }

    private void drawNodes(GuiGraphics guiGraphics, PlayerMagicState state, int mouseX, int mouseY, int clipTop, int clipBottom) {
        for (MagicalClassDefinition definition : MagicalClasses.all()) {
            ClassTreeLayout.Node node = ClassTreeLayout.get(definition.id());
            if (node == null) {
                continue;
            }
            // Bases are the hubs of the graph, so give them a little more presence.
            boolean isBase = definition.isBase();
            int w = isBase ? Math.round(nodeWidth() * 1.12F) : nodeWidth();
            int h = isBase ? Math.round(nodeHeight() * 1.2F) : nodeHeight();
            int x = screenX(node) - w / 2;
            int y = screenY(node) - h / 2;
            if (y + h < clipTop || y > clipBottom) {
                continue;
            }
            boolean owned = state.hasClass(definition.id());
            boolean affordable = !owned && state.canEvolveClass(definition.id());
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h
                    && mouseY >= clipTop && mouseY < clipBottom;
            if (hover) {
                hovered = definition.id();
            }
            int accent = accentFor(definition.id());
            int base = owned ? MagicalGuiStyle.brighten(accent, 0.42F) : affordable ? 0xFF2E3B55 : 0xFF1A2130;
            MagicalGuiStyle.card(guiGraphics, x, y, x + w, y + h, base);
            if (owned || affordable || hover) {
                int edge = owned ? accent : affordable ? MagicalGuiStyle.withAlpha(accent, 0xAA) : MagicalGuiStyle.withAlpha(accent, 0x55);
                guiGraphics.fill(x, y, x + w, y + 1, edge);
                guiGraphics.fill(x, y + h - 1, x + w, y + h, edge);
                guiGraphics.fill(x, y, x + 1, y + h, edge);
                guiGraphics.fill(x + w - 1, y, x + w, y + h, edge);
            }
            // Below this the text is unreadable anyway and only produces stubs; the tooltip carries
            // the full name at any zoom.
            if (zoom >= 0.40F) {
                String name = font.plainSubstrByWidth(Component.translatable(definition.nameKey()).getString(), w - 6);
                guiGraphics.drawCenteredString(font, name, x + w / 2, y + (h - 8) / 2,
                        owned ? MagicalGuiStyle.TEXT_PRIMARY : affordable ? 0xBFD7FF : MagicalGuiStyle.TEXT_MUTED);
            }
        }
    }

    private void drawFooter(GuiGraphics guiGraphics, PlayerMagicState state, int left, int top) {
        int y = top + imageHeight - 22;
        MagicalGuiStyle.inset(guiGraphics, left + 8, y - 4, left + imageWidth - 8, y + 18);

        StringBuilder pools = new StringBuilder();
        for (MagicalClassDefinition base : MagicalClasses.startingRoots()) {
            if (state.hasClass(base.id())) {
                if (pools.length() > 0) {
                    pools.append("   ");
                }
                pools.append(Component.translatable(base.nameKey()).getString())
                        .append(": ")
                        .append(state.classXpPool(base.id()));
            }
        }
        guiGraphics.drawString(font, pools.length() == 0
                        ? Component.translatable("screen.magical.class_tree_hint").getString()
                        : pools.toString(),
                left + 14, y + 2, MagicalGuiStyle.TEXT_PRIMARY, false);

        MagicalGuiStyle.button(guiGraphics, font, left + imageWidth - 68, y, 58, 16, 0xFF27354A,
                Component.translatable("screen.magical.back"));
        if (state.hasClass(MagicalClasses.BLACKSMITH)) {
            MagicalGuiStyle.button(guiGraphics, font, left + imageWidth - 194, y, 60, 16, 0xFF345C42,
                    Component.translatable("screen.magical.open_forge"));
        }
        if (state.hasClass(MagicalClasses.SPELL_CREATOR)) {
            MagicalGuiStyle.button(guiGraphics, font, left + imageWidth - 130, y, 58, 16, 0xFF3F315C,
                    Component.translatable("screen.magical.open_spell_creator"));
        }
    }

    private void drawNodeTooltip(GuiGraphics guiGraphics, MagicalClassDefinition definition, PlayerMagicState state, int mouseX, int mouseY) {
        if (definition == null) {
            return;
        }
        int width = 210;
        int textWidth = width - 16;
        List<String> grants = grantLines(definition);
        int descriptionHeight = wrappedHeight(Component.translatable(definition.descriptionKey()), textWidth);
        int height = 30 + descriptionHeight + grants.size() * 10 + 14;

        int x = Mth.clamp(mouseX + 12, leftPos + 6, leftPos + imageWidth - width - 6);
        int y = Mth.clamp(mouseY + 12, topPos + 6, topPos + imageHeight - height - 6);
        int accent = accentFor(definition.id());
        MagicalGuiStyle.panel(guiGraphics, x, y, x + width, y + height, accent);

        int textY = y + 7;
        guiGraphics.drawString(font, Component.translatable(definition.nameKey()), x + 8, textY, accent, false);
        textY += 12;
        textY += drawWrapped(guiGraphics, Component.translatable(definition.descriptionKey()), x + 8, textY, textWidth) + 4;
        for (String grant : grants) {
            guiGraphics.drawString(font, grant, x + 8, textY, 0x8FEA9C, false);
            textY += 10;
        }

        boolean owned = state.hasClass(definition.id());
        Component status;
        int statusColour;
        if (owned) {
            status = Component.translatable("screen.magical.unlocked");
            statusColour = 0x8FEA9C;
        } else if (definition.isBase()) {
            status = Component.translatable("screen.magical.class_locked");
            statusColour = MagicalGuiStyle.TEXT_MUTED;
        } else if (definition.parents().stream().noneMatch(state::hasClass)) {
            status = Component.translatable("screen.magical.class_tree_needs_parent");
            statusColour = 0xD66A6A;
        } else {
            int pool = state.classXpPool(definition.id());
            status = Component.translatable("screen.magical.class_tree_cost", definition.xpCost(), pool);
            statusColour = pool >= definition.xpCost() ? 0x8FEA9C : 0xD66A6A;
        }
        guiGraphics.drawString(font, status, x + 8, y + height - 12, statusColour, false);
    }

    private List<String> grantLines(MagicalClassDefinition definition) {
        List<String> lines = new ArrayList<>();
        for (ResourceLocation skillId : definition.rewardSkills()) {
            MagicSkillDefinition skill = MagicContent.get(skillId);
            if (skill != null) {
                lines.add(Component.translatable("screen.magical.class_tree_grants_skill",
                        Component.translatable(skill.nameKey())).getString());
            }
        }
        for (ResourceLocation passiveId : definition.rewardPassives()) {
            MagicPassiveDefinition passive = MagicPassiveContent.get(passiveId);
            if (passive != null) {
                lines.add(Component.translatable("screen.magical.class_tree_grants_passive",
                        Component.translatable(passive.nameKey())).getString());
            }
        }
        return lines;
    }

    private int drawWrapped(GuiGraphics guiGraphics, Component text, int x, int y, int width) {
        List<net.minecraft.util.FormattedCharSequence> lines = font.split(text, width);
        for (int i = 0; i < lines.size(); i++) {
            guiGraphics.drawString(font, lines.get(i), x, y + i * 10, MagicalGuiStyle.TEXT_PRIMARY, false);
        }
        return lines.size() * 10;
    }

    private int wrappedHeight(Component text, int width) {
        return font.split(text, width).size() * 10;
    }

    private int accentFor(ResourceLocation id) {
        ResourceLocation baseId = MagicalClasses.baseOf(id);
        List<MagicalClassDefinition> bases = MagicalClasses.startingRoots();
        for (int i = 0; i < bases.size(); i++) {
            if (bases.get(i).id().equals(baseId)) {
                return ACCENTS[i % ACCENTS.length];
            }
        }
        return MagicalGuiStyle.ACCENT_ARCANE;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        // Drawn last and lifted on z, the way vanilla renders tooltips, so neither the node labels
        // nor anything else in the deferred text batch can bleed through the panel.
        if (hovered != null) {
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, 400.0F);
            drawNodeTooltip(guiGraphics, MagicalClasses.get(hovered), ClientMagicState.get(), mouseX, mouseY);
            guiGraphics.pose().popPose();
        }
    }

    // ---- interaction ----

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0) {
            float extent = ClassTreeLayout.extent() + 120.0F;
            panX = Mth.clamp(panX + (float) (dragX / zoom), -extent, extent);
            panY = Mth.clamp(panY + (float) (dragY / zoom), -extent, extent);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D) {
            // Proportional steps so zooming out stays usable at the low end of the range.
            zoom = Mth.clamp(zoom * (scrollY > 0.0D ? 1.12F : 1.0F / 1.12F), MIN_ZOOM, MAX_ZOOM);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PlayerMagicState state = ClientMagicState.get();
        int y = topPos + imageHeight - 22;
        if (inside(mouseX, mouseY, leftPos + imageWidth - 68, y, 58, 16)) {
            press(ClassTreeMenu.BUTTON_OPEN_CODEX);
            return true;
        }
        if (state.hasClass(MagicalClasses.BLACKSMITH) && inside(mouseX, mouseY, leftPos + imageWidth - 194, y, 60, 16)) {
            press(ClassTreeMenu.BUTTON_OPEN_FORGE);
            return true;
        }
        if (state.hasClass(MagicalClasses.SPELL_CREATOR) && inside(mouseX, mouseY, leftPos + imageWidth - 130, y, 58, 16)) {
            press(ClassTreeMenu.BUTTON_OPEN_SPELL_CREATOR);
            return true;
        }
        if (hovered != null && state.canEvolveClass(hovered)) {
            List<MagicalClassDefinition> nodes = ClassTreeMenu.nodes();
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).id().equals(hovered)) {
                    press(ClassTreeMenu.BUTTON_EVOLVE_BASE + i);
                    return true;
                }
            }
        }
        return true;
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }
}
