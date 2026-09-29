package com.efkrdnz.magical.client.screen.mind;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.hud.HudDebug;
import com.efkrdnz.magical.client.hud.HudQuiet;
import com.efkrdnz.magical.client.mind.BeltHotbarOverlay;
import com.efkrdnz.magical.client.mind.DaydreamMode;
import com.efkrdnz.magical.client.mind.LexiconShelves;
import com.efkrdnz.magical.client.mind.LexiconShelves.Shelf;
import com.efkrdnz.magical.client.mind.LieIcons;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.mind.Belt;
import com.efkrdnz.magical.magic.mind.Impression;
import com.efkrdnz.magical.magic.mind.Lexicon;
import com.efkrdnz.magical.magic.mind.MindGazeService;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Lexicon inventory: vanilla creative's inventory, showing only what the wielder has learned,
 * shelved in the creative tabs those things live in, with the belt of nine lies where creative
 * draws the hotbar. A plain screen with no menu and no slots behind it: a lie is its key, the icons
 * are pictures from {@link LieIcons}, and the only thing that ever leaves is a key, through
 * {@link MagicalNetwork#sendSetBeltSlot}.
 */
public final class LexiconInventoryScreen extends Screen implements HudDebug.Captured, HudQuiet {
    private static final ResourceLocation PANEL = ResourceLocation.withDefaultNamespace("textures/gui/container/creative_inventory/tab_items.png");
    private static final ResourceLocation SEARCH_PANEL = ResourceLocation.withDefaultNamespace("textures/gui/container/creative_inventory/tab_item_search.png");
    private static final ResourceLocation SCROLLER = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller");
    private static final ResourceLocation SCROLLER_DISABLED = ResourceLocation.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    private static final ResourceLocation HIGHLIGHT_BACK = ResourceLocation.withDefaultNamespace("container/slot_highlight_back");
    private static final ResourceLocation HIGHLIGHT_FRONT = ResourceLocation.withDefaultNamespace("container/slot_highlight_front");
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HOVER = ResourceLocation.withDefaultNamespace("widget/button_highlighted");
    private static final ResourceLocation BUTTON_OFF = ResourceLocation.withDefaultNamespace("widget/button_disabled");
    private static final ResourceLocation[] TAB_UNSELECTED = tabSprites("unselected");
    private static final ResourceLocation[] TAB_SELECTED = tabSprites("selected");
    private static final String SEARCH = "search";
    private static final int LABEL = 0x404040;
    private static final int FRAME = 0xFFFFFFFF;
    private static final int TEXTURE_SIZE = 256;
    private static final int MAX_FIDELITY = 3;
    /** Where creative lifts a floating item, over everything but the tooltip. */
    private static final float CARRIED_Z = 232.0F;
    private static final float TAB_ICON_Z = 100.0F;

    /** The tab last looked at, by id, so the Lexicon reopens where it was left, as creative does. */
    private static String lastTab;

    private final List<TabView> tabs = new ArrayList<>();
    private final Map<String, ItemStack> icons = new HashMap<>();
    private List<String> shown = List.of();
    private int selectedTab;
    private float scroll;
    /** How many lies the wielder knew when the tabs were shelved, so a new one reshelves them. */
    private int builtFrom;
    private boolean scrolling;
    /** The lie on the cursor, by key; null when the cursor is empty. */
    private String carried;
    private EditBox searchBox;

    /** A tab as drawn: its id, its title, its icon and the known keys shelved on it. */
    private record TabView(String id, Component title, ItemStack icon, List<String> keys) {
    }

    private LexiconInventoryScreen() {
        super(Component.translatable("mind.magical.lexicon.title"));
    }

    /** Opens the Lexicon, only while Daydreaming: it is the Daydream's inventory, not the player's. */
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && DaydreamMode.active()) {
            minecraft.setScreen(new LexiconInventoryScreen());
        }
    }

    private static ResourceLocation[] tabSprites(String state) {
        ResourceLocation[] sprites = new ResourceLocation[7];
        for (int i = 0; i < sprites.length; i++) {
            sprites[i] = ResourceLocation.withDefaultNamespace("container/creative_inventory/tab_top_" + state + "_" + (i + 1));
        }
        return sprites;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private LexiconInventoryLayout layout() {
        return new LexiconInventoryLayout(width, height, !DaydreamMode.dreaming());
    }

    private Lexicon lexicon() {
        return ClientMagicState.get().mind().lexicon();
    }

    private Belt belt() {
        return ClientMagicState.get().mind().belt();
    }

    // ---- the shelves ---------------------------------------------------------------------------

    @Override
    protected void init() {
        LexiconInventoryLayout layout = layout();
        Rect box = layout.search();
        String query = searchBox == null ? "" : searchBox.getValue();
        searchBox = new EditBox(font, box.x(), box.y(), box.w(), box.h(), Component.translatable("mind.magical.lexicon.search"));
        searchBox.setMaxLength(50);
        searchBox.setBordered(false);
        searchBox.setTextColor(0xFFFFFF);
        searchBox.setValue(query);
        addWidget(searchBox);
        buildTabs();
        int remembered = indexOf(lastTab);
        selectTab(remembered >= 0 ? remembered : Math.min(1, tabs.size() - 1));
    }

    private void buildTabs() {
        tabs.clear();
        builtFrom = lexicon().size();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        HolderLookup.Provider registries = player.level().registryAccess();
        // Exactly as the creative screen rebuilds its contents, so the shelves are what it would show.
        CreativeModeTabs.tryRebuildTabContents(player.connection.enabledFeatures(),
                player.canUseGameMasterBlocks() && minecraft.options.operatorItemsTab().get(), registries);
        Map<String, CreativeModeTab> byId = new HashMap<>();
        List<Shelf> categories = new ArrayList<>();
        for (CreativeModeTab tab : CreativeModeTabs.tabs()) {
            if (tab.getType() != CreativeModeTab.Type.CATEGORY) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (id == null) {
                continue;
            }
            byId.put(id.toString(), tab);
            categories.add(new Shelf(id.toString(), keysOf(tab, registries)));
        }
        tabs.add(new TabView(SEARCH, Component.translatable("mind.magical.lexicon.search"), new ItemStack(Items.COMPASS), List.of()));
        for (Shelf shelf : LexiconShelves.shelve(categories, lexicon().keys())) {
            CreativeModeTab tab = byId.get(shelf.id());
            if (tab != null) {
                tabs.add(new TabView(shelf.id(), tab.getDisplayName(), tab.getIconItem(), shelf.keys()));
            } else {
                tabs.add(new TabView(shelf.id(), Component.translatable("mind.magical.lexicon.other"), new ItemStack(Items.PAPER), shelf.keys()));
            }
        }
    }

    /** A tab's contents as impression keys: blocks by their block, creatures by their egg; anything else is no lie. */
    private static List<String> keysOf(CreativeModeTab tab, HolderLookup.Provider registries) {
        List<String> keys = new ArrayList<>();
        for (ItemStack stack : tab.getDisplayItems()) {
            if (stack.getItem() instanceof BlockItem block) {
                keys.add(Impression.block(BuiltInRegistries.BLOCK.getKey(block.getBlock()).toString()).key());
            } else if (stack.getItem() instanceof SpawnEggItem egg) {
                EntityType<?> type = egg.getType(registries, stack);
                keys.add(Impression.creature(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()).key());
            }
        }
        return keys;
    }

    private int indexOf(String id) {
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private boolean onSearch() {
        return selectedTab == 0;
    }

    private void selectTab(int index) {
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        selectedTab = index;
        lastTab = tabs.get(index).id();
        scroll = 0.0F;
        boolean search = onSearch();
        searchBox.setVisible(search);
        searchBox.setCanLoseFocus(!search);
        searchBox.setFocused(search);
        setFocused(search ? searchBox : null);
        refilter();
    }

    private void refilter() {
        if (onSearch()) {
            shown = LexiconShelves.search(lexicon().keys(), key -> MindGazeService.displayName(key).getString(), searchBox.getValue());
        } else {
            shown = tabs.get(selectedTab).keys();
        }
        // A list that no longer overflows has nothing to scroll: a thumb left down there would jump it later.
        if (hiddenRows() == 0) {
            scroll = 0.0F;
        }
    }

    // ---- scrolling -----------------------------------------------------------------------------

    /** Rows past the five the grid shows. */
    private int hiddenRows() {
        int rows = (shown.size() + LexiconInventoryLayout.COLUMNS - 1) / LexiconInventoryLayout.COLUMNS;
        return Math.max(0, rows - LexiconInventoryLayout.ROWS);
    }

    private int firstRow() {
        return Math.max(0, (int) (scroll * hiddenRows() + 0.5F));
    }

    /** The key in a grid cell at the current scroll, or null for an empty cell. */
    private String gridKey(int cell) {
        if (cell < 0) {
            return null;
        }
        int index = firstRow() * LexiconInventoryLayout.COLUMNS + cell;
        return index < shown.size() ? shown.get(index) : null;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        if (!layout().onPanel(mx, my) || hiddenRows() == 0) {
            return false;
        }
        scroll = Math.max(0.0F, Math.min(1.0F, scroll - (float) (dy / hiddenRows())));
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (scrolling) {
            scroll = layout().scrollFor(my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        scrolling = false;
        return super.mouseReleased(mx, my, button);
    }

    // ---- the mouse -----------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        LexiconInventoryLayout layout = layout();
        if (clickTabs(layout, mx, my) || clickPages(layout, mx, my)) {
            return true;
        }
        if (LexiconInventoryLayout.hit(layout.scroller(), mx, my)) {
            scrolling = hiddenRows() > 0;
            if (scrolling) {
                scroll = layout.scrollFor(my);
            }
            return true;
        }
        int cell = layout.gridAt(mx, my);
        if (cell >= 0) {
            clickGrid(gridKey(cell));
            return true;
        }
        int slot = layout.beltAt(mx, my);
        if (slot >= 0) {
            clickBelt(slot);
            return true;
        }
        if (!layout.onFrame(mx, my)) {
            // Let go of what is carried, as a stack dropped outside creative's panel is gone.
            carried = null;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean clickTabs(LexiconInventoryLayout layout, double mx, double my) {
        if (layout.onPlaybill(mx, my)) {
            carried = null;
            PlaybillScreen.open();
            return true;
        }
        int column = layout.tabAt(mx, my);
        int index = page(layout) * layout.tabsPerPage() + column;
        if (column >= 0 && index < tabs.size()) {
            selectTab(index);
            return true;
        }
        return false;
    }

    private boolean clickPages(LexiconInventoryLayout layout, double mx, double my) {
        if (pages(layout) <= 1) {
            return false;
        }
        int page = page(layout);
        if (LexiconInventoryLayout.hit(layout.pagePrev(), mx, my)) {
            if (page > 0) {
                selectTab((page - 1) * layout.tabsPerPage());
            }
            return true;
        }
        if (LexiconInventoryLayout.hit(layout.pageNext(), mx, my)) {
            if (page + 1 < pages(layout)) {
                selectTab((page + 1) * layout.tabsPerPage());
            }
            return true;
        }
        return false;
    }

    private void clickGrid(String key) {
        if (key == null) {
            carried = null;
            return;
        }
        if (hasShiftDown()) {
            int slot = firstEmptyBeltSlot();
            setBelt(slot >= 0 ? slot : DaydreamMode.selected(), key);
            return;
        }
        carried = key;
    }

    private void clickBelt(int slot) {
        String there = belt().get(slot);
        if (carried != null) {
            setBelt(slot, carried);
            carried = there;
        } else if (there != null) {
            carried = there;
            setBelt(slot, null);
        }
    }

    private int firstEmptyBeltSlot() {
        for (int i = 0; i < Belt.SIZE; i++) {
            if (belt().get(i) == null) {
                return i;
            }
        }
        return -1;
    }

    /** Every belt change: the server hears the key, and the client's own belt shows it at once until the sync lands. */
    private void setBelt(int slot, String key) {
        belt().set(slot, key);
        MagicalNetwork.sendSetBeltSlot(slot, key);
        if (slot == DaydreamMode.selected()) {
            BeltHotbarOverlay.selectionChanged();
        }
    }

    private int pages(LexiconInventoryLayout layout) {
        return Math.max(1, (tabs.size() + layout.tabsPerPage() - 1) / layout.tabsPerPage());
    }

    private int page(LexiconInventoryLayout layout) {
        return selectedTab / layout.tabsPerPage();
    }

    // ---- the keys ------------------------------------------------------------------------------

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        // On Search the box takes every key, the inventory key and the number row included, as creative's does.
        if (onSearch() && searchBox.isFocused()) {
            String before = searchBox.getValue();
            if (searchBox.keyPressed(key, scan, modifiers) && !before.equals(searchBox.getValue())) {
                refilter();
            }
            return true;
        }
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        if (minecraft != null && hotbarKey(key, scan)) {
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    /** 1-9 over a lie or a belt slot: that belt slot takes it, and a belt slot trades places, as the hotbar does. */
    private boolean hotbarKey(int key, int scan) {
        for (int i = 0; i < Belt.SIZE; i++) {
            if (!minecraft.options.keyHotbarSlots[i].matches(key, scan)) {
                continue;
            }
            double[] mouse = mouse();
            LexiconInventoryLayout layout = layout();
            String hovered = gridKey(layout.gridAt(mouse[0], mouse[1]));
            int slot = layout.beltAt(mouse[0], mouse[1]);
            if (hovered != null) {
                setBelt(i, hovered);
            } else if (slot >= 0 && slot != i) {
                String there = belt().get(slot);
                String target = belt().get(i);
                setBelt(i, there);
                setBelt(slot, target);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!onSearch()) {
            return false;
        }
        String before = searchBox.getValue();
        if (searchBox.charTyped(codePoint, modifiers)) {
            if (!before.equals(searchBox.getValue())) {
                refilter();
            }
            return true;
        }
        return false;
    }

    private double[] mouse() {
        var handler = minecraft.mouseHandler;
        var window = minecraft.getWindow();
        return new double[]{
                handler.xpos() * window.getGuiScaledWidth() / window.getScreenWidth(),
                handler.ypos() * window.getGuiScaledHeight() / window.getScreenHeight()};
    }

    @Override
    public void tick() {
        // The Daydream ended under it (it left the loadout, the wielder strayed or died): so does its inventory.
        if (!DaydreamMode.active()) {
            onClose();
            return;
        }
        // A lie learned with the screen open joins its own tab as well as Search.
        if (lexicon().size() != builtFrom && !tabs.isEmpty()) {
            String keep = tabs.get(selectedTab).id();
            buildTabs();
            int index = indexOf(keep);
            selectedTab = index >= 0 ? index : Math.min(selectedTab, tabs.size() - 1);
            refilter();
        }
    }

    @Override
    public void removed() {
        // Closing lets go of what is carried: it was only ever a name on the cursor.
        carried = null;
        super.removed();
    }

    // ---- drawing -------------------------------------------------------------------------------

    @Override
    public void renderBackground(GuiGraphics graphics, int mx, int my, float partialTick) {
        renderTransparentBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mx, int my, float partialTick) {
        super.render(graphics, mx, my, partialTick);
        LexiconInventoryLayout layout = layout();
        int page = page(layout);
        drawTabs(graphics, layout, page, false);
        graphics.blit(RenderType::guiTextured, onSearch() ? SEARCH_PANEL : PANEL, layout.left(), layout.top(),
                0.0F, 0.0F, LexiconInventoryLayout.PANEL_W, LexiconInventoryLayout.PANEL_H, TEXTURE_SIZE, TEXTURE_SIZE);
        if (onSearch()) {
            searchBox.render(graphics, mx, my, partialTick);
        }
        Rect thumb = layout.thumb(scroll);
        graphics.blitSprite(RenderType::guiTextured, hiddenRows() > 0 ? SCROLLER : SCROLLER_DISABLED, thumb.x(), thumb.y(), thumb.w(), thumb.h());
        drawTabs(graphics, layout, page, true);
        drawPageArrows(graphics, layout, page, mx, my);
        graphics.drawString(font, tabs.isEmpty() ? title : tabs.get(selectedTab).title(), layout.left() + 8, layout.top() + 6, LABEL, false);
        drawCells(graphics, layout, mx, my);
        if (lexicon().keys().isEmpty()) {
            drawEmpty(graphics, layout);
        }
        if (carried != null) {
            graphics.pose().pushPose();
            graphics.pose().translate(0.0F, 0.0F, CARRIED_Z);
            graphics.renderItem(icon(carried), mx - 8, my - 8);
            graphics.pose().popPose();
        }
        drawTooltip(graphics, layout, page, mx, my);
    }

    /** The tabs of this page: first every one not chosen, under the panel, then the chosen one over it. */
    private void drawTabs(GuiGraphics graphics, LexiconInventoryLayout layout, int page, boolean chosen) {
        int from = page * layout.tabsPerPage();
        for (int column = 0; column < layout.tabsPerPage() && from + column < tabs.size(); column++) {
            boolean isChosen = from + column == selectedTab;
            if (isChosen == chosen) {
                drawTab(graphics, layout.tab(column), column, isChosen, tabs.get(from + column).icon());
            }
        }
        if (!chosen && layout.playbill()) {
            drawTab(graphics, layout.playbillTab(), TAB_UNSELECTED.length - 1, false, new ItemStack(Items.WRITABLE_BOOK));
        }
    }

    /** Creative's {@code renderTabButton} for a top tab. */
    private void drawTab(GuiGraphics graphics, Rect rect, int column, boolean chosen, ItemStack icon) {
        ResourceLocation[] sprites = chosen ? TAB_SELECTED : TAB_UNSELECTED;
        graphics.blitSprite(RenderType::guiTextured, sprites[Math.min(column, sprites.length - 1)], rect.x(), rect.y(), rect.w(), rect.h());
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, TAB_ICON_Z);
        graphics.renderItem(icon, rect.x() + 5, rect.y() + 9);
        graphics.pose().popPose();
    }

    private void drawPageArrows(GuiGraphics graphics, LexiconInventoryLayout layout, int page, int mx, int my) {
        int pages = pages(layout);
        if (pages <= 1) {
            return;
        }
        drawArrow(graphics, layout.pagePrev(), "<", page > 0, mx, my);
        drawArrow(graphics, layout.pageNext(), ">", page + 1 < pages, mx, my);
    }

    private void drawArrow(GuiGraphics graphics, Rect rect, String glyph, boolean enabled, int mx, int my) {
        ResourceLocation sprite = !enabled ? BUTTON_OFF : LexiconInventoryLayout.hit(rect, mx, my) ? BUTTON_HOVER : BUTTON;
        graphics.blitSprite(RenderType::guiTextured, sprite, rect.x(), rect.y(), rect.w(), rect.h());
        graphics.drawString(font, glyph, rect.x() + (rect.w() - font.width(glyph) + 1) / 2, rect.y() + 2, enabled ? 0xFFFFFF : 0xA0A0A0);
    }

    /** The grid at its scroll and the belt, with creative's hover highlight and a white frame round the lie in hand. */
    private void drawCells(GuiGraphics graphics, LexiconInventoryLayout layout, int mx, int my) {
        int hoveredCell = layout.gridAt(mx, my);
        int hoveredSlot = layout.beltAt(mx, my);
        Rect hovered = hoveredCell >= 0 ? layout.grid(hoveredCell) : hoveredSlot >= 0 ? layout.belt(hoveredSlot) : null;
        if (hovered != null) {
            graphics.blitSprite(RenderType::guiTextured, HIGHLIGHT_BACK, hovered.x() - 4, hovered.y() - 4, 24, 24);
        }
        for (int i = 0; i < LexiconInventoryLayout.GRID_CELLS; i++) {
            String key = gridKey(i);
            if (key != null) {
                Rect cell = layout.grid(i);
                graphics.renderItem(icon(key), cell.x(), cell.y());
            }
        }
        for (int i = 0; i < Belt.SIZE; i++) {
            String key = belt().get(i);
            Rect cell = layout.belt(i);
            if (key != null) {
                graphics.renderItem(icon(key), cell.x(), cell.y());
            }
        }
        Rect selected = layout.belt(DaydreamMode.selected());
        graphics.renderOutline(selected.x() - 1, selected.y() - 1, selected.w() + 2, selected.h() + 2, FRAME);
        if (hovered != null) {
            graphics.blitSprite(RenderType::guiTexturedOverlay, HIGHLIGHT_FRONT, hovered.x() - 4, hovered.y() - 4, 24, 24);
        }
    }

    private void drawEmpty(GuiGraphics graphics, LexiconInventoryLayout layout) {
        Rect first = layout.grid(0);
        Rect last = layout.grid(LexiconInventoryLayout.GRID_CELLS - 1);
        int width = last.right() - first.x();
        List<FormattedCharSequence> lines = font.split(Component.translatable("mind.magical.lexicon.empty"), width);
        int y = first.y() + (last.bottom() - first.y() - lines.size() * font.lineHeight) / 2;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, first.x() + (width - font.width(line)) / 2, y, LABEL, false);
            y += font.lineHeight;
        }
    }

    private void drawTooltip(GuiGraphics graphics, LexiconInventoryLayout layout, int page, int mx, int my) {
        if (layout.onPlaybill(mx, my)) {
            graphics.renderTooltip(font, Component.translatable("mind.magical.lexicon.playbill"), mx, my);
            return;
        }
        int column = layout.tabAt(mx, my);
        int index = page * layout.tabsPerPage() + column;
        if (column >= 0 && index < tabs.size()) {
            graphics.renderTooltip(font, tabs.get(index).title(), mx, my);
            return;
        }
        if (carried != null) {
            return;
        }
        int slot = layout.beltAt(mx, my);
        String key = slot >= 0 ? belt().get(slot) : gridKey(layout.gridAt(mx, my));
        if (key != null) {
            graphics.renderComponentTooltip(font, List.of(MindGazeService.displayName(key), fidelityMarks(lexicon().fidelity(key))), mx, my);
        }
    }

    /** One filled diamond a level of fidelity out of three: {@code ◆◆◇}. */
    private static Component fidelityMarks(int fidelity) {
        int filled = Math.max(0, Math.min(MAX_FIDELITY, fidelity));
        return Component.literal("◆".repeat(filled) + "◇".repeat(MAX_FIDELITY - filled)).withStyle(ChatFormatting.GRAY);
    }

    /** A picture of a lie, made once per key while the screen is open; it is drawn and never handed to anything. */
    private ItemStack icon(String key) {
        return icons.computeIfAbsent(key, LieIcons::stack);
    }
}
