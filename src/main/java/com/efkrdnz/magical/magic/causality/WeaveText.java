package com.efkrdnz.magical.magic.causality;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A chain, said out loud.
 *
 * <p>A graph is a picture, and a picture of a rule is not the same thing as knowing what the rule
 * does. The design goal for this Authority is that a complex chain reads at a glance, and the glance
 * is only half of it: the board draws the shape and this writes the sentence, and between them a
 * wielder can check that what they drew is what they meant without running out into a fight to find
 * out.
 *
 * <p>The same text is used by {@code /magical causality show}, so the reading on the screen and the
 * reading in the chat can never drift apart and say two different things about one board.
 */
public final class WeaveText {

    /** What separates one pin from the next when a chain is written out. */
    public static final String LINK = " › ";

    private WeaveText() {}

    /** One chain: every pin in order, with its number and, on the last one, where it lands. */
    public static Component chain(WeaveReview.Chain chain) {
        MutableComponent line = Component.empty();
        List<CausalNode> pins = chain.pins();
        for (int i = 0; i < pins.size(); i++) {
            if (i > 0) {
                line.append(Component.literal(LINK).withStyle(ChatFormatting.DARK_GRAY));
            }
            line.append(pin(pins.get(i)));
        }
        if (!chain.complete()) {
            line.append(Component.literal(LINK).withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable("message.magical.weave_trails_off").withStyle(ChatFormatting.DARK_GRAY));
        }
        return line;
    }

    /** One pin: its word, its number when it has one, and its scope when it is a consequence. */
    public static Component pin(CausalNode node) {
        MutableComponent text = Component.translatable(node.translationKey())
                .withStyle(style(node.kind()));
        String number = paramText(node);
        if (!number.isEmpty()) {
            text.append(Component.literal(" " + number).withStyle(ChatFormatting.WHITE));
        }
        if (node.kind() == NodeKind.EFFECT) {
            for (Modifier modifier : node.modifiers()) {
                text.append(Component.literal(" "))
                        .append(Component.translatable(modifier.translationKey()).withStyle(ChatFormatting.GRAY));
            }
            text.append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY))
                    .append(Component.translatable(node.scope().translationKey()).withStyle(ChatFormatting.GRAY));
        }
        return text;
    }

    /**
     * The number on a pin in whatever unit it is actually in.
     *
     * <p>Ticks are seconds, shares are percentages, ranges are blocks. A board that showed every one
     * of them as a bare integer would be asking the wielder to remember which of the three a given
     * pin deals in, which is precisely the kind of bookkeeping that makes a system feel like work.
     */
    public static String paramText(CausalNode node) {
        if (!node.takesParam()) {
            return "";
        }
        int value = node.param();
        return switch (node.kind()) {
            case CAUSE -> switch (node.cause()) {
                case TOLL -> seconds(value);
                case NEAR -> value + "m";
                default -> String.valueOf(value);
            };
            case CONDITION -> switch (node.condition()) {
                case ONCE_PER -> seconds(value);
                case WITHIN -> value + "m";
                case HURT_UNDER, HALE_OVER, SETTLED -> value + "%";
                default -> String.valueOf(value);
            };
            case EFFECT -> switch (node.effect()) {
                case STORE -> value + "%";
                case BIND, KINDLE -> seconds(value);
                default -> String.valueOf(value);
            };
        };
    }

    private static String seconds(int ticks) {
        return ticks % 20 == 0 ? (ticks / 20) + "s" : String.format(java.util.Locale.ROOT, "%.1fs", ticks / 20.0F);
    }

    /** What is wrong with a pin, in one line, named so the wielder can find it on the board. */
    public static Component issue(WeaveReview.Issue issue, Weave weave) {
        CausalNode node = weave.node(issue.nodeId());
        Component name = node == null ? Component.literal("?") : Component.translatable(node.translationKey());
        return Component.translatable(issue.kind().translationKey(), name)
                .withStyle(issue.kind() == WeaveReview.Kind.WANTS_MARK ? ChatFormatting.YELLOW : ChatFormatting.RED);
    }

    /** The whole board in one line: how many chains would fire, and what it costs to hold. */
    public static Component summary(Weave weave) {
        return Component.translatable("message.magical.weave_summary",
                WeaveReview.live(weave).size(), weave.weight(), weave.capacity());
    }

    private static ChatFormatting style(NodeKind kind) {
        return switch (kind) {
            case CAUSE -> ChatFormatting.GOLD;
            case CONDITION -> ChatFormatting.AQUA;
            case EFFECT -> ChatFormatting.RED;
        };
    }
}
