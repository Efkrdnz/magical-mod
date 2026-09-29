package com.efkrdnz.magical.client.screen.mind;

import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.hud.HudDebug;
import com.efkrdnz.magical.client.hud.HudQuiet;
import com.efkrdnz.magical.client.mind.DaydreamMode;
import com.efkrdnz.magical.client.screen.CodexLayout.Rect;
import com.efkrdnz.magical.magic.mind.Belief;
import com.efkrdnz.magical.magic.mind.Figment;
import com.efkrdnz.magical.magic.mind.ImaginedBlock;
import com.efkrdnz.magical.magic.mind.LevelMindWorld;
import com.efkrdnz.magical.magic.mind.Lexicon;
import com.efkrdnz.magical.magic.mind.MindGazeService;
import com.efkrdnz.magical.magic.mind.MindService;
import com.efkrdnz.magical.magic.mind.MindState;
import com.efkrdnz.magical.magic.mind.Offset;
import com.efkrdnz.magical.magic.mind.Plausibility;
import com.efkrdnz.magical.magic.mind.Reaction;
import com.efkrdnz.magical.magic.mind.Reverie;
import com.efkrdnz.magical.magic.mind.ReverieNbt;
import com.efkrdnz.magical.magic.mind.Script;
import com.efkrdnz.magical.magic.mind.Sense;
import com.efkrdnz.magical.magic.mind.Stance;
import com.efkrdnz.magical.magic.mind.Susceptibility;
import com.efkrdnz.magical.magic.mind.UnveilCost;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** The reverie editor: elements, the selected element's script and senses, and the Forecast. */
public final class PlaybillScreen extends Screen implements HudDebug.Captured, HudQuiet {
    private static final int SCRIM = 0xA6060B14;
    private static final int BRIGHT = 0xFFFFFFFF;
    private static final int MUTED = 0xFF8C86A0;
    private static final int LIT = 0xFFEFC86A;
    private static final int LILAC = 0xFFBDA4FF;
    private static final int GAIN = 0xFF9FE0A8;
    private static final int LOSS = 0xFFF08C8C;
    private static final String[] FORECAST_VIEWERS = {"minecraft:zombie", "minecraft:skeleton", "minecraft:player", "minecraft:enderman"};

    private int slot;
    private Reverie working;
    private int selected;
    private boolean dirty;

    private PlaybillScreen() {
        super(Component.translatable("screen.magical.playbill"));
    }

    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.setScreen(new PlaybillScreen());
        }
    }

    @Override
    protected void init() {
        if (working == null) {
            load(DaydreamMode.active() ? DaydreamMode.slot() : ClientMagicState.get().mind().activeSlot());
        }
    }

    private void load(int next) {
        slot = next;
        working = DaydreamMode.active() && DaydreamMode.slot() == next
                ? DaydreamMode.draft().copy() : ClientMagicState.get().mind().reverie(next).copy();
        selected = 0;
        dirty = false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private PlaybillLayout layout() {
        return new PlaybillLayout(width, height);
    }

    private int clusterCount() {
        return working.clusters().size();
    }

    private boolean figmentSelected() {
        return selected >= clusterCount() && selected < clusterCount() + working.figments().size();
    }

    private Set<Sense> selectedSenses() {
        if (selected < clusterCount()) {
            return working.clusters().get(selected).get(0).senses();
        }
        return figmentSelected() ? working.figments().get(selected - clusterCount()).senses() : Set.of();
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        // The key that opened the Playbill from Daydream closes it again.
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(x, y, button);
        }
        PlaybillLayout layout = layout();
        int tab = layout.tabAt(x, y);
        if (tab >= 0 && tab < MindState.SLOTS) {
            if (tab != slot) {
                if (dirty) {
                    save();
                }
                load(tab);
            }
            return true;
        }
        int row = layout.rowAt(x, y);
        if (row >= 0 && row < clusterCount() + working.figments().size()) {
            selected = row;
            return true;
        }
        int sense = layout.senseAt(x, y);
        if (sense >= 0 && (selected < clusterCount() || figmentSelected())) {
            EnumSet<Sense> next = selectedSenses().isEmpty() ? EnumSet.noneOf(Sense.class) : EnumSet.copyOf(selectedSenses());
            Sense flipped = Sense.values()[sense];
            if (!next.remove(flipped)) {
                next.add(flipped);
            }
            if (selected < clusterCount()) {
                working.setClusterSenses(working.clusters().get(selected).get(0).at(), next);
            } else {
                working.setFigmentSenses(selected - clusterCount(), next);
            }
            dirty = true;
            return true;
        }
        if (figmentSelected()) {
            int index = selected - clusterCount();
            Script script = working.figments().get(index).script();
            int stance = layout.stanceAt(x, y);
            int reaction = layout.reactionAt(x, y);
            if (stance >= 0) {
                working.setScript(index, new Script(Stance.values()[stance], script.reaction()));
                dirty = true;
                return true;
            }
            if (reaction >= 0) {
                working.setScript(index, new Script(script.stance(), Reaction.values()[reaction]));
                dirty = true;
                return true;
            }
        }
        if (PlaybillLayout.inside(layout.save(), x, y)) {
            save();
            return true;
        }
        if (PlaybillLayout.inside(layout.done(), x, y)) {
            onClose();
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    private void save() {
        MagicalNetwork.sendSaveReverie(slot, ReverieNbt.save(working));
        if (DaydreamMode.active() && DaydreamMode.slot() == slot) {
            DaydreamMode.setDraft(working);
        }
        dirty = false;
    }

    @Override
    public void onClose() {
        if (dirty) {
            save();
        }
        super.onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, SCRIM);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        PlaybillLayout layout = layout();
        for (int i = 0; i < MindState.SLOTS; i++) {
            Rect tab = layout.tab(i);
            text(g, Component.translatable("screen.magical.playbill.slot", i + 1), tab, i == slot ? BRIGHT : MUTED);
            if (i == slot) {
                g.fill(tab.x(), tab.bottom() - 1, tab.right(), tab.bottom(), LILAC);
            }
        }
        List<Component> names = elementNames();
        if (names.isEmpty()) {
            text(g, Component.translatable("screen.magical.playbill.empty"), layout.row(0), MUTED);
        }
        for (int i = 0; i < names.size() && i < layout.rows(); i++) {
            text(g, names.get(i), layout.row(i), i == selected ? BRIGHT : MUTED);
        }
        if (!names.isEmpty()) {
            renderScript(g, layout);
            renderForecast(g, layout);
        }
        text(g, Component.translatable("screen.magical.playbill.save"), layout.save(), dirty ? LIT : MUTED);
        text(g, Component.translatable("screen.magical.playbill.done"), layout.done(), BRIGHT);
    }

    private List<Component> elementNames() {
        List<Component> names = new ArrayList<>();
        for (List<ImaginedBlock> cluster : working.clusters()) {
            names.add(Component.translatable("screen.magical.playbill.cluster",
                    MindGazeService.displayName("block:" + cluster.get(0).blockId()), cluster.size()));
        }
        for (Figment figment : working.figments()) {
            names.add(MindGazeService.displayName("creature:" + figment.creatureId()));
        }
        return names;
    }

    private void renderScript(GuiGraphics g, PlaybillLayout layout) {
        Rect heading = layout.sense(0);
        g.drawString(font, Component.translatable("screen.magical.playbill.senses"), heading.x(), heading.y() - PlaybillLayout.LINE, MUTED, true);
        Set<Sense> senses = selectedSenses();
        for (Sense sense : Sense.values()) {
            text(g, Component.translatable("mind.magical.sense." + lower(sense)), layout.sense(sense.ordinal()),
                    senses.contains(sense) ? LIT : MUTED);
        }
        if (!figmentSelected()) {
            return;
        }
        Script script = working.figments().get(selected - clusterCount()).script();
        g.drawString(font, Component.translatable("screen.magical.playbill.stance"), layout.stance(0).x(),
                layout.stance(0).y() - PlaybillLayout.LINE, MUTED, true);
        g.drawString(font, Component.translatable("screen.magical.playbill.reaction"), layout.reaction(0).x(),
                layout.reaction(0).y() - PlaybillLayout.LINE, MUTED, true);
        for (Stance stance : Stance.values()) {
            text(g, Component.translatable("mind.magical.stance." + lower(stance)), layout.stance(stance.ordinal()),
                    stance == script.stance() ? LIT : MUTED);
        }
        for (Reaction reaction : Reaction.values()) {
            text(g, Component.translatable("mind.magical.reaction." + lower(reaction)), layout.reaction(reaction.ordinal()),
                    reaction == script.reaction() ? LIT : MUTED);
        }
    }

    private void renderForecast(GuiGraphics g, PlaybillLayout layout) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        int x = layout.forecastX();
        int y = layout.contentTop();
        int width = layout.forecastWidth();
        BlockHitResult aim = unveilAim(minecraft);
        if (aim == null) {
            // Unveil refuses with nothing to stand on, so there is no spot to forecast.
            for (FormattedCharSequence wrapped : font.split(Component.translatable("screen.magical.playbill.look_at_ground"), width)) {
                g.drawString(font, wrapped, x, y, MUTED, true);
                y += PlaybillLayout.LINE;
            }
            line(g, Component.translatable("screen.magical.playbill.cost"), UnveilCost.of(working) + "", x, y + 4, width, LILAC);
            return;
        }
        BlockPos anchor = aim.getBlockPos().relative(aim.getDirection());
        int turns = minecraft.player.getDirection().get2DDataValue() - working.facing();
        Lexicon lexicon = ClientMagicState.get().mind().lexicon();
        LevelMindWorld world = new LevelMindWorld(minecraft.level);
        Plausibility.Reading reading;
        if (selected < clusterCount()) {
            List<Plausibility.Placed> placed = new ArrayList<>();
            for (ImaginedBlock block : working.clusters().get(selected)) {
                BlockPos at = at(anchor, block.at(), turns);
                placed.add(new Plausibility.Placed(at.getX(), at.getY(), at.getZ(), block.blockId()));
            }
            reading = Plausibility.cluster(world, placed, lexicon, working.size());
        } else {
            Figment figment = working.figments().get(selected - clusterCount());
            BlockPos at = at(anchor, figment.at(), turns);
            reading = Plausibility.figment(world, new Plausibility.Placed(at.getX(), at.getY(), at.getZ(), figment.creatureId()),
                    figment.script(), lexicon, working.size());
        }
        y = line(g, Component.translatable("screen.magical.playbill.plausibility"), String.format(Locale.ROOT, "%.2f", reading.p()), x, y, width, BRIGHT);
        for (Plausibility.Term term : reading.terms()) {
            y = line(g, Component.translatable("mind.magical.term." + term.key()),
                    String.format(Locale.ROOT, "%+.2f", term.value()), x, y, width, term.value() >= 0 ? GAIN : LOSS);
        }
        float senses = Sense.multiplier(selectedSenses());
        y = line(g, Component.translatable("screen.magical.playbill.senses"), String.format(Locale.ROOT, "x%.2f", senses), x, y, width, BRIGHT);
        y += 4;
        g.drawString(font, Component.translatable("screen.magical.playbill.certain_in"), x, y, MUTED, true);
        y += PlaybillLayout.LINE;
        for (String viewer : FORECAST_VIEWERS) {
            int ticks = Belief.ticksToReach(Belief.SURE, reading.p(), senses, Susceptibility.of(viewer), 1.0F);
            Component who = EntityType.byString(viewer).map(EntityType::getDescription).orElse(Component.literal(viewer));
            String value = ticks < 0 ? Component.translatable("screen.magical.playbill.never").getString()
                    : String.format(Locale.ROOT, "%.1fs", ticks / 20.0F);
            y = line(g, who, value, x, y, width, BRIGHT);
        }
        y += 4;
        line(g, Component.translatable("screen.magical.playbill.cost"), UnveilCost.of(working) + "", x, y, width, LILAC);
    }

    /**
     * Where Unveil would land: the block its own aim reaches, along the same ray and the same
     * distance ({@link MindService#UNVEIL_REACH}), or null when that ray meets no block.
     */
    private static BlockHitResult unveilAim(Minecraft minecraft) {
        Vec3 from = minecraft.player.getEyePosition();
        Vec3 to = from.add(minecraft.player.getLookAngle().normalize().scale(MindService.UNVEIL_REACH));
        BlockHitResult hit = minecraft.level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, minecraft.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private static BlockPos at(BlockPos anchor, Offset offset, int turns) {
        Offset turned = offset.rotate(turns);
        return anchor.offset(turned.dx(), turned.dy(), turned.dz());
    }

    /** A label left and its value right-aligned, on one line of the forecast; returns the next line's y. */
    private int line(GuiGraphics g, Component label, String value, int x, int y, int width, int valueColour) {
        int valueWidth = font.width(value);
        String cut = font.plainSubstrByWidth(label.getString(), Math.max(0, width - valueWidth - 6));
        g.drawString(font, cut, x, y, MUTED, true);
        g.drawString(font, value, x + width - valueWidth, y, valueColour, true);
        return y + PlaybillLayout.LINE;
    }

    private void text(GuiGraphics g, Component text, Rect rect, int colour) {
        String cut = font.plainSubstrByWidth(text.getString(), rect.w());
        g.drawString(font, cut, rect.x(), rect.y() + 2, colour, true);
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
