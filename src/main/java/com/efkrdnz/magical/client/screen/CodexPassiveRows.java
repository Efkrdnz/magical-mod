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
 * list is sixty-odd rows long, so both halves of one are lifted to the top under their own
 * headings rather than sorted in with the permanent passives, where they would be unfindable
 * exactly when the clock on them matters.
 *
 * <p>Everything below the pact keeps the order it has always had. Declaration order in
 * {@link MagicPassiveContent} already runs general, then the sins, then the five class lines in
 * tree order, so emitting a header whenever the group changes is enough and there is no separate
 * sort that could fall out of step with it.
 */
public final class CodexPassiveRows {

    public static final String GROUP_RITUAL_BOON = "ritual_boon";
    public static final String GROUP_RITUAL_PRICE = "ritual_price";
    public static final String GROUP_SINS = "sins";
    public static final String GROUP_GENERAL = "general";

    private static final int TICKS_PER_SECOND = 20;

    /**
     * One line of the list: a group header, or an owned passive.
     *
     * <p>{@code index} is the passive's position in {@link MagicPassiveContent#normalPassives()},
     * which is what the toggle button id is built from; a header carries -1. {@code ticks} is what
     * is left of a pact clock, and zero for everything that has no clock at all.
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

    /** The whole list, pact first. */
    public static List<Row> rows(PlayerMagicState state) {
        List<Row> rows = new ArrayList<>();
        List<MagicPassiveDefinition> all = MagicPassiveContent.normalPassives();
        ritualSection(rows, state, all, true);
        ritualSection(rows, state, all, false);

        String group = null;
        for (int index = 0; index < all.size(); index++) {
            MagicPassiveDefinition definition = all.get(index);
            if (!state.hasPassive(definition.id()) || state.ritualRemaining(definition.id()) > 0) {
                // A pact passive has already been listed above; listing it twice would give it two
                // checkboxes and two different countdowns.
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
        rows.add(header(Component.translatable("screen.magical.passive_group_" + group), group));
        rows.addAll(section);
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
