package com.efkrdnz.magical.client.screen;

import com.efkrdnz.magical.classes.MagicalClassDefinition;
import com.efkrdnz.magical.classes.MagicalClasses;
import com.efkrdnz.magical.magic.MagicPassiveContent;
import com.efkrdnz.magical.magic.MagicPassiveDefinition;
import com.efkrdnz.magical.magic.PlayerMagicState;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * What the codex's passives list is made of: the owned passives, in the order they are shown.
 *
 * <p>Out of the screen because the order is the interesting part. A pact runs for minutes and the
 * list is sixty-odd rows long, so a pact entry is lifted to the top of its column under its own
 * heading rather than sorted in with the permanent ones, where it would be unfindable exactly when
 * the clock on it matters.
 *
 * <p>The tab has two columns and the pact has two halves, so they line up: {@link #rows} builds the
 * passives column and puts the boons at the top of it, {@link #curseRows} builds the curses column
 * and puts the prices at the top of that. A price is registered as a normal passive so it can carry
 * a clock, but what it is to the player is a curse, and it belongs in the column that says so.
 *
 * <p>Everything below the pact keeps the order it has always had. Declaration order in
 * {@link MagicPassiveContent} already runs general, then the sins, then the five class lines in
 * tree order, so emitting a header whenever the group changes is enough and there is no separate
 * sort that could fall out of step with it.
 */
public final class CodexPassiveRows {

    public static final String GROUP_RITUAL_BOON = "ritual_boon";
    public static final String GROUP_RITUAL_PRICE = "ritual_price";
    public static final String GROUP_LASTING_CURSE = "lasting_curse";
    public static final String GROUP_SINS = "sins";
    public static final String GROUP_GENERAL = "general";

    private static final int TICKS_PER_SECOND = 20;

    /**
     * One line of the list: a group header, or an owned passive.
     *
     * <p>{@code index} is the row's position in the roster its button id is built from:
     * {@link MagicPassiveContent#normalPassives()} for a passives-column row, whose button is the
     * toggle, and {@link MagicPassiveContent#curses()} for a lasting curse, whose button is Dispel.
     * A header carries -1, and so does a temporary curse, which has no button at all: -1 falls
     * outside both bands, so a button wired off it by mistake does nothing rather than acting on
     * whichever entry happens to be first. {@code ticks} is what is left of a pact clock, and zero
     * for everything that has no clock.
     */
    public record Row(Component header, String groupKey, MagicPassiveDefinition definition, int index, int ticks) {

        public boolean isHeader() {
            return definition == null;
        }

        public boolean ritual() {
            return ticks > 0;
        }
    }

    private CodexPassiveRows() {}

    /** The passives column: the boon half of a pact, then everything permanent. */
    public static List<Row> rows(PlayerMagicState state) {
        List<Row> rows = new ArrayList<>();
        List<MagicPassiveDefinition> all = MagicPassiveContent.normalPassives();
        ritualSection(rows, state, all, true);

        String group = null;
        for (int index = 0; index < all.size(); index++) {
            MagicPassiveDefinition definition = all.get(index);
            if (!state.hasPassive(definition.id()) || state.ritualRemaining(definition.id()) > 0) {
                // A pact entry is listed by its own section: a boon above, a price in the other
                // column entirely. Listing one here as well would give it two rows, two
                // countdowns and, for a boon, a second checkbox.
                continue;
            }
            String next = groupKey(definition);
            if (!next.equals(group)) {
                group = next;
                rows.add(header(label(definition, next), next));
            }
            rows.add(new Row(null, next, definition, index, 0));
        }
        return rows;
    }

    /**
     * The curses column: the price half of a pact, then the curses that stay until dispelled.
     *
     * <p>A price is drawn here without a Dispel button, because the clock is the only way out of
     * one - that is the whole of what was bought with it. The lasting heading is only emitted when
     * there is a temporary section above it to be told apart from, so a player who has never sealed
     * a pact sees the flat list this column has always been.
     */
    public static List<Row> curseRows(PlayerMagicState state) {
        List<Row> temporary = new ArrayList<>();
        for (MagicPassiveDefinition definition : MagicPassiveContent.normalPassives()) {
            int ticks = state.ritualRemaining(definition.id());
            if (ticks > 0 && MagicPassiveContent.isRitualPrice(definition.id())) {
                temporary.add(new Row(null, GROUP_RITUAL_PRICE, definition, -1, ticks));
            }
        }
        List<Row> lasting = new ArrayList<>();
        List<MagicPassiveDefinition> curses = MagicPassiveContent.curses();
        for (int index = 0; index < curses.size(); index++) {
            MagicPassiveDefinition definition = curses.get(index);
            if (state.hasCurse(definition.id())) {
                lasting.add(new Row(null, GROUP_LASTING_CURSE, definition, index, 0));
            }
        }

        List<Row> rows = new ArrayList<>();
        if (!temporary.isEmpty()) {
            rows.add(header(label(GROUP_RITUAL_PRICE), GROUP_RITUAL_PRICE));
            rows.addAll(temporary);
            if (!lasting.isEmpty()) {
                rows.add(header(label(GROUP_LASTING_CURSE), GROUP_LASTING_CURSE));
            }
        }
        rows.addAll(lasting);
        return rows;
    }

    /** One half of a pact, with its heading, or nothing at all when that half is empty. */
    private static void ritualSection(List<Row> rows, PlayerMagicState state,
            List<MagicPassiveDefinition> all, boolean boons) {
        String group = boons ? GROUP_RITUAL_BOON : GROUP_RITUAL_PRICE;
        List<Row> section = new ArrayList<>();
        for (int index = 0; index < all.size(); index++) {
            MagicPassiveDefinition definition = all.get(index);
            int ticks = state.ritualRemaining(definition.id());
            if (ticks <= 0 || MagicPassiveContent.isRitualBoon(definition.id()) != boons) {
                continue;
            }
            section.add(new Row(null, group, definition, index, ticks));
        }
        if (section.isEmpty()) {
            return;
        }
        rows.add(header(label(group), group));
        rows.addAll(section);
    }

    private static Component label(String group) {
        return Component.translatable("screen.magical.passive_group_" + group);
    }

    private static Row header(Component label, String group) {
        return new Row(label, group, null, -1, 0);
    }

    private static String groupKey(MagicPassiveDefinition definition) {
        if (MagicPassiveContent.isSinPassive(definition.id())) {
            return GROUP_SINS;
        }
        ResourceLocation source = MagicalClasses.classGranting(definition.id());
        if (source == null || !MagicPassiveContent.isClassPassive(definition.id())) {
            return GROUP_GENERAL;
        }
        return MagicalClasses.baseOf(source).toString();
    }

    private static Component label(MagicPassiveDefinition definition, String key) {
        if (GROUP_SINS.equals(key)) {
            return Component.translatable("screen.magical.passive_group_sins");
        }
        if (GROUP_GENERAL.equals(key)) {
            return Component.translatable("screen.magical.passive_group_general");
        }
        MagicalClassDefinition base =
                MagicalClasses.get(MagicalClasses.baseOf(MagicalClasses.classGranting(definition.id())));
        return base == null ? Component.translatable("screen.magical.passive_group_general")
                : Component.translatable(base.nameKey());
    }

    /** A clock as m:ss. Never negative, so a stale tick cannot read as an hour. */
    public static String countdown(int ticks) {
        int seconds = Math.max(0, ticks) / TICKS_PER_SECOND;
        return seconds / 60 + ":" + String.format(Locale.ROOT, "%02d", seconds % 60);
    }
}
