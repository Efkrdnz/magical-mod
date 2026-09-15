package com.efkrdnz.magical.client.screen.sacrifice;

import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.BOON_X;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.CAPTION_W;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.LIST_W;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.LIST_Y;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.PANEL_H;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.PANEL_W;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.PRICE_X;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.ROWS;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.ROW_STRIDE;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.TOOLTIP_W;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.boonCost;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.boonLabel;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.boonMark;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.boonRow;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.boonRowAt;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.boonScrollbar;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.boonText;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.brokerNote;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.caption;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.chosen;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.clampScroll;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.clocks;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.close;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.hint;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.hit;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.pactLabel;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.pactPanel;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.pointsBar;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.priceCost;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.priceLabel;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.priceMark;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.priceRow;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.priceRowAt;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.priceScrollbar;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.priceText;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.requirement;
import static com.efkrdnz.magical.client.screen.sacrifice.BloodSacrificeLayout.sealButton;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.hud.HudDebug;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.client.screen.MagicalGuiStyle;
import com.efkrdnz.magical.client.screen.ScreenChrome;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.blood.BloodSacrificeService;
import com.efkrdnz.magical.magic.blood.SacrificeBudget;
import com.efkrdnz.magical.magic.blood.SacrificeCatalogue;
import com.efkrdnz.magical.network.MagicalNetwork;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * The Blood Sacrifice pact screen: choose what you gain, then choose what it costs you.
 *
 * <p>Three columns. Boons on the left spend points; prices in the middle earn the right to have
 * spent them; the pact on the right says whether the two balance and seals them. Nothing here is
 * authoritative - every rule it draws is checked again by {@code BloodSacrificeService.validate}
 * when the seal packet lands - but a screen that let you build a pact the server would refuse
 * would be a worse screen, so it runs the same arithmetic on the way in.
 *
 * <p>A plain {@link Screen} with no menu behind it, like the Spell Creator: the only thing it
 * sends is the seal, and everything it reads is already in the synced state. Each frame paints
 * chrome fills first and text afterwards, so nothing lands under its own label.
 */
public final class BloodSacrificeScreen extends Screen implements HudDebug.Captured {

    /** How long a sent seal keeps the button held before the screen gives up waiting. */
    private static final int PENDING_TIMEOUT_TICKS = 60;

    private static final int SEAL_BASE = 0xFF7A2233;
    private static final int DISABLED_BASE = 0xFF27354A;
    private static final int BOON_TINT = 0xFF7FD4A0;
    private static final int PRICE_TINT = 0xFFE06470;
    private static final int COST_CHIP = 0xFF16202F;
    private static final int LINE_H = 10;
    private static final int TICKS_PER_SECOND = 20;

    private final Set<ResourceLocation> boons = new LinkedHashSet<>();
    private final Set<ResourceLocation> prices = new LinkedHashSet<>();

    private final int boonTicks;
    private final int priceTicks;

    private int leftPos;
    private int topPos;
    private int boonScroll;
    private int priceScroll;
    private boolean pending;
    private int pendingTicks;
    /** The last refusal the screen itself raised, shown on the hint row until the next click. */
    private Component note = Component.empty();

    public BloodSacrificeScreen(int boonTicks, int priceTicks) {
        super(Component.translatable("screen.magical.sacrifice"));
        this.boonTicks = boonTicks;
        this.priceTicks = priceTicks;
    }

    /** The one way in. Closes whatever container is open first, which also tells the server. */
    public static void open(int boonTicks, int priceTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        if (minecraft.player.containerMenu != minecraft.player.inventoryMenu) {
            minecraft.player.closeContainer();
        }
        minecraft.setScreen(new BloodSacrificeScreen(boonTicks, priceTicks));
    }

    @Override
    protected void init() {
        leftPos = (width - PANEL_W) / 2;
        topPos = (height - PANEL_H) / 2;
    }

    /** A paused integrated server would never answer the seal. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        if (pending && ++pendingTicks > PENDING_TIMEOUT_TICKS) {
            pending = false;
        }
    }

    // ---- drawing ----------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        PlayerMagicState state = state();
        int x0 = leftPos;
        int y0 = topPos;
        double lx = mouseX - x0;
        double ly = mouseY - y0;

        MagicalGuiStyle.screenBackground(g, x0, y0, x0 + PANEL_W, y0 + PANEL_H);
        ScreenChrome.paintHeader(g, font, x0, y0, MagicalGuiStyle.ACCENT_BLOOD, pointsFraction(state),
                Component.translatable("screen.magical.close"));
        ScreenChrome.paintBody(g, x0, y0, MagicalGuiStyle.ACCENT_BLOOD);
        paintColumn(g, x0, y0, lx, ly, true);
        paintColumn(g, x0, y0, lx, ly, false);
        paintPact(g, state, x0, y0);

        ScreenChrome.textHeader(g, font, x0, y0, getTitle(), vesselChipLabel(state),
                vesselFull(state) ? MagicalGuiStyle.ACCENT_BLOOD : MagicalGuiStyle.TEXT_MUTED, pointsLabel(state));
        textColumns(g, x0, y0);
        textPact(g, state, x0, y0);

        List<Component> tooltip = tooltipAt(lx, ly);
        if (!tooltip.isEmpty()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component line : tooltip) {
                lines.addAll(font.split(line, TOOLTIP_W));
            }
            g.renderTooltip(font, lines, mouseX, mouseY);
        }
    }

    private void paintColumn(GuiGraphics g, int x0, int y0, double lx, double ly, boolean boonSide) {
        List<ResourceLocation> entries = entries(boonSide);
        int scroll = boonSide ? boonScroll : priceScroll;
        int left = boonSide ? BOON_X : PRICE_X;
        MagicalGuiStyle.inset(g, x0 + left - 2, y0 + LIST_Y - 2, x0 + left + LIST_W + 2,
                y0 + LIST_Y + (ROWS - 1) * ROW_STRIDE + ROW_STRIDE - 2);
        int hovered = boonSide ? boonRowAt(lx, ly) : priceRowAt(lx, ly);
        int visible = Math.min(ROWS, entries.size() - scroll);
        for (int row = 0; row < visible; row++) {
            ResourceLocation id = entries.get(scroll + row);
            Rect rect = boonSide ? boonRow(row) : priceRow(row);
            boolean picked = picked(boonSide).contains(id);
            MagicalGuiStyle.listRow(g, x0 + rect.x(), y0 + rect.y(), rect.w(), rect.h(), picked || row == hovered,
                    boonSide ? BOON_TINT : PRICE_TINT);
            Rect chip = boonSide ? boonCost(row) : priceCost(row);
            g.fill(x0 + chip.x(), y0 + chip.y(), x0 + chip.right(), y0 + chip.bottom(), COST_CHIP);
            Rect mark = boonSide ? boonMark(row) : priceMark(row);
            MagicalGuiStyle.checkbox(g, x0 + mark.x(), y0 + mark.y(), picked, boonSide ? BOON_TINT : PRICE_TINT);
        }
        Rect bar = boonSide ? boonScrollbar() : priceScrollbar();
        MagicalGuiStyle.scrollbar(g, x0 + bar.x() + 1, y0 + bar.y(), bar.h(), entries.size(), ROWS, scroll);
    }

    private void paintPact(GuiGraphics g, PlayerMagicState state, int x0, int y0) {
        Rect panel = pactPanel();
        MagicalGuiStyle.panel(g, x0 + panel.x(), y0 + panel.y(), x0 + panel.right(), y0 + panel.bottom(),
                MagicalGuiStyle.ACCENT_BLOOD);
        Rect button = sealButton();
        MagicalGuiStyle.button(g, font, x0 + button.x(), y0 + button.y(), button.w(), button.h(),
                sealable(state) && !pending ? SEAL_BASE : DISABLED_BASE,
                pending ? Component.translatable("screen.magical.sacrifice.sealing")
                        : Component.translatable("screen.magical.sacrifice.seal"));
    }

    private void textColumns(GuiGraphics g, int x0, int y0) {
        MagicalGuiStyle.sectionLabel(g, font, x0 + boonLabel().x(), y0 + boonLabel().y(),
                Component.translatable("screen.magical.sacrifice.boons"), MagicalGuiStyle.TEXT_PRIMARY);
        MagicalGuiStyle.sectionLabel(g, font, x0 + priceLabel().x(), y0 + priceLabel().y(),
                Component.translatable("screen.magical.sacrifice.prices"), MagicalGuiStyle.TEXT_PRIMARY);
        MagicalGuiStyle.sectionLabel(g, font, x0 + pactLabel().x(), y0 + pactLabel().y(),
                Component.translatable("screen.magical.sacrifice.pact"), MagicalGuiStyle.TEXT_PRIMARY);
        Rect line = caption();
        g.drawString(font, font.plainSubstrByWidth(
                        Component.translatable("screen.magical.sacrifice.caption").getString(), CAPTION_W),
                x0 + line.x(), y0 + line.y(), MagicalGuiStyle.TEXT_MUTED, false);

        textRows(g, x0, y0, true);
        textRows(g, x0, y0, false);

        Rect hintRow = hint();
        boolean plain = note.getString().isEmpty();
        Component shown = plain ? Component.translatable("screen.magical.sacrifice.hint") : note;
        g.drawString(font, font.plainSubstrByWidth(shown.getString(), hintRow.w()),
                x0 + hintRow.x(), y0 + hintRow.y(),
                plain ? MagicalGuiStyle.TEXT_MUTED : PRICE_TINT & 0xFFFFFF, false);
    }

    private void textRows(GuiGraphics g, int x0, int y0, boolean boonSide) {
        List<ResourceLocation> entries = entries(boonSide);
        int scroll = boonSide ? boonScroll : priceScroll;
        int visible = Math.min(ROWS, entries.size() - scroll);
        for (int row = 0; row < visible; row++) {
            ResourceLocation id = entries.get(scroll + row);
            Rect chip = boonSide ? boonCost(row) : priceCost(row);
            g.drawCenteredString(font, String.valueOf(SacrificeCatalogue.cost(id)),
                    x0 + chip.x() + chip.w() / 2, y0 + chip.y() + 3,
                    (boonSide ? BOON_TINT : PRICE_TINT) & 0xFFFFFF);
            Rect text = boonSide ? boonText(row) : priceText(row);
            g.drawString(font, font.plainSubstrByWidth(name(id).getString(), text.w()),
                    x0 + text.x(), y0 + text.y(), MagicalGuiStyle.TEXT_PRIMARY, false);
        }
    }

    private void textPact(GuiGraphics g, PlayerMagicState state, int x0, int y0) {
        boolean broker = broker(state);
        int spent = spent();
        int paid = SacrificeCatalogue.sum(List.copyOf(prices), false);
        int required = SacrificeBudget.priceRequired(spent, broker);

        Rect need = requirement();
        g.drawString(font, Component.translatable("screen.magical.sacrifice.required", paid, required),
                x0 + need.x(), y0 + need.y(),
                (paid >= required ? BOON_TINT : PRICE_TINT) & 0xFFFFFF, false);
        g.drawString(font, Component.translatable("screen.magical.sacrifice.spent", spent, budget(state)),
                x0 + need.x(), y0 + need.y() + 11, MagicalGuiStyle.TEXT_MUTED, false);

        Rect clock = clocks();
        g.drawString(font, Component.translatable("screen.magical.sacrifice.boon_clock", clock(boonTicks)),
                x0 + clock.x(), y0 + clock.y(), BOON_TINT & 0xFFFFFF, false);
        g.drawString(font, Component.translatable("screen.magical.sacrifice.price_clock", clock(priceTicks)),
                x0 + clock.x(), y0 + clock.y() + 11, PRICE_TINT & 0xFFFFFF, false);

        Rect list = chosen();
        int y = y0 + list.y();
        int bottom = y0 + list.bottom();
        y = drawChosen(g, boons, x0 + list.x(), y, bottom, list.w(), BOON_TINT & 0xFFFFFF);
        drawChosen(g, prices, x0 + list.x(), y, bottom, list.w(), PRICE_TINT & 0xFFFFFF);

        if (broker) {
            Rect brokerRow = brokerNote();
            String amplification = String.format(Locale.ROOT, "%.2f",
                    SacrificeBudget.amplification(prices.size(), true));
            g.drawString(font, Component.translatable("screen.magical.sacrifice.broker", amplification),
                    x0 + brokerRow.x(), y0 + brokerRow.y(), MagicalGuiStyle.ACCENT_GOLD, false);
        }
    }

    /** Names down the pact column until the panel runs out; returns the next free line. */
    private int drawChosen(GuiGraphics g, Set<ResourceLocation> ids, int x, int y, int bottom, int width, int color) {
        for (ResourceLocation id : ids) {
            if (y + LINE_H > bottom) {
                return y;
            }
            g.drawString(font, font.plainSubstrByWidth(name(id).getString(), width), x, y, color, false);
            y += LINE_H;
        }
        return y;
    }

    // ---- tooltips ----------------------------------------------------------------------------------

    private List<Component> tooltipAt(double lx, double ly) {
        int row = boonRowAt(lx, ly);
        if (row >= 0 && boonScroll + row < SacrificeCatalogue.BOONS.size()) {
            return entryTooltip(SacrificeCatalogue.BOONS.get(boonScroll + row), true);
        }
        row = priceRowAt(lx, ly);
        if (row >= 0 && priceScroll + row < SacrificeCatalogue.PRICES.size()) {
            return entryTooltip(SacrificeCatalogue.PRICES.get(priceScroll + row), false);
        }
        if (hit(pointsBar(), lx, ly)) {
            return List.of(Component.translatable("screen.magical.sacrifice.points_tooltip"));
        }
        return List.of();
    }

    private static List<Component> entryTooltip(ResourceLocation id, boolean boonSide) {
        return List.of(
                name(id).copy().withColor((boonSide ? BOON_TINT : PRICE_TINT) & 0xFFFFFF),
                Component.translatable("screen.magical.sacrifice.cost", SacrificeCatalogue.cost(id))
                        .withColor(MagicalGuiStyle.TEXT_MUTED),
                Component.translatable(descriptionKey(id)));
    }

    // ---- input --------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        double lx = mouseX - leftPos;
        double ly = mouseY - topPos;
        if (hit(close(), lx, ly)) {
            onClose();
            return true;
        }
        int row = boonRowAt(lx, ly);
        if (row >= 0 && boonScroll + row < SacrificeCatalogue.BOONS.size()) {
            toggleBoon(SacrificeCatalogue.BOONS.get(boonScroll + row));
            return true;
        }
        row = priceRowAt(lx, ly);
        if (row >= 0 && priceScroll + row < SacrificeCatalogue.PRICES.size()) {
            toggle(prices, SacrificeCatalogue.PRICES.get(priceScroll + row));
            note = Component.empty();
            return true;
        }
        if (hit(sealButton(), lx, ly)) {
            seal();
        }
        return true;
    }

    /** A boon may only go on if its cost still fits the budget; the hint row says why it did not. */
    private void toggleBoon(ResourceLocation id) {
        note = Component.empty();
        if (boons.remove(id)) {
            return;
        }
        if (spent() + SacrificeCatalogue.cost(id) > budget(state())) {
            note = Component.translatable("screen.magical.sacrifice.no_points");
            return;
        }
        boons.add(id);
    }

    private static void toggle(Set<ResourceLocation> set, ResourceLocation id) {
        if (!set.remove(id)) {
            set.add(id);
        }
    }

    private void seal() {
        PlayerMagicState state = state();
        if (pending) {
            return;
        }
        if (!sealable(state)) {
            note = refusal(state);
            return;
        }
        MagicalNetwork.sendBloodSacrificeSeal(List.copyOf(boons), List.copyOf(prices));
        pending = true;
        pendingTicks = 0;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        double lx = mouseX - leftPos;
        double ly = mouseY - topPos;
        int step = (int) Math.signum(scrollY);
        if (boonRowAt(lx, ly) >= 0) {
            boonScroll = clampScroll(boonScroll - step, SacrificeCatalogue.BOONS.size(), ROWS);
        } else if (priceRowAt(lx, ly) >= 0) {
            priceScroll = clampScroll(priceScroll - step, SacrificeCatalogue.PRICES.size(), ROWS);
        }
        return true;
    }

    // ---- the same arithmetic the server runs ----------------------------------------------------------

    private boolean sealable(PlayerMagicState state) {
        if (boons.isEmpty() || !vesselFull(state)) {
            return false;
        }
        return spent() <= budget(state)
                && SacrificeCatalogue.sum(List.copyOf(prices), false)
                        >= SacrificeBudget.priceRequired(spent(), broker(state));
    }

    private Component refusal(PlayerMagicState state) {
        if (boons.isEmpty()) {
            return BloodSacrificeService.Refusal.NOTHING_CHOSEN.message();
        }
        if (!vesselFull(state)) {
            return BloodSacrificeService.Refusal.VESSEL_NOT_FULL.message();
        }
        return BloodSacrificeService.Refusal.PRICES_TOO_CHEAP.message();
    }

    private int spent() {
        return SacrificeCatalogue.sum(List.copyOf(boons), true);
    }

    private int budget(PlayerMagicState state) {
        return SacrificeBudget.boonBudget(state.tuningFor(MagicContent.BLOOD_SACRIFICE.id()), broker(state));
    }

    private static boolean broker(PlayerMagicState state) {
        return state.isPassiveEnabled(MagicPassiveContent.HELLBROKER.id());
    }

    private static boolean vesselFull(PlayerMagicState state) {
        return state.bloodVessel() >= BloodSacrificeService.RITUAL_COST;
    }

    private float pointsFraction(PlayerMagicState state) {
        int budget = budget(state);
        return budget <= 0 ? 0.0F : Math.min(1.0F, spent() / (float) budget);
    }

    private Component pointsLabel(PlayerMagicState state) {
        return Component.translatable("screen.magical.sacrifice.points", spent(), budget(state));
    }

    private static Component vesselChipLabel(PlayerMagicState state) {
        return Component.translatable("screen.magical.sacrifice.vessel", state.bloodVessel(),
                BloodSacrificeService.RITUAL_COST);
    }

    // ---- odds and ends ---------------------------------------------------------------------------------

    private static List<ResourceLocation> entries(boolean boonSide) {
        return boonSide ? SacrificeCatalogue.BOONS : SacrificeCatalogue.PRICES;
    }

    /** Not named {@code chosen}: that is the layout's rectangle for the pact column. */
    private Set<ResourceLocation> picked(boolean boonSide) {
        return boonSide ? boons : prices;
    }

    private static Component name(ResourceLocation id) {
        MagicPassiveDefinition definition = MagicPassiveContent.get(id);
        return Component.translatable(definition == null ? id.toString() : definition.nameKey());
    }

    private static String descriptionKey(ResourceLocation id) {
        MagicPassiveDefinition definition = MagicPassiveContent.get(id);
        return definition == null ? id.toString() : definition.descriptionKey();
    }

    /** Ticks as m:ss, the same shape the codex countdown uses. */
    public static String clock(int ticks) {
        int seconds = Math.max(0, ticks) / TICKS_PER_SECOND;
        return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }

    private static PlayerMagicState state() {
        return ClientMagicState.get();
    }
}
