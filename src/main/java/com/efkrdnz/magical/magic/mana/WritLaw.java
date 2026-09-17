package com.efkrdnz.magical.magic.mana;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.MagicSkillResolvedStats;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the Ledgers of the world have to say about the cast that is about to happen.
 *
 * <p>This is the half of the Authority of Mana that does the work, and it is a <em>read</em>, not a
 * push. A subspace reaches out every tick and moves whatever it finds; a Ledger sits in someone
 * pocket and is consulted at the one instant that matters - when a cast resolves its numbers. A
 * caster nobody has legislated against pays exactly what they always paid, and the cost of that
 * being true is one loop over the online players.
 *
 * <p><b>Two writs cancel.</b> When two wielders have both ruled on the same aspect of the same
 * spell for the same caster and they disagree, the aspect goes neutral - the spell behaves
 * ordinarily and neither wielder is legislating. That is not a special case bolted on; it falls out
 * of two gods giving the same order differently, and it is the cleanest in-fiction argument for one
 * wielder per Authority.
 */
public final class WritLaw {

    private WritLaw() {}

    /**
     * Every number a writ may move, moved, in the one place every cast resolves its numbers.
     *
     * <p>Authority skills are exempt. Authority is uncounterable everywhere else in the mod and a
     * tax is a counter, so the Ledger cannot be used to price another god out of their own power.
     */
    public static MagicSkillResolvedStats apply(ServerPlayer caster, MagicSkillResolvedStats stats) {
        MagicSkillDefinition definition = stats.definition();
        if (caster == null || definition == null || MagicContent.isAuthoritySkill(definition.id())) {
            return stats;
        }
        int mana = scaledInt(caster, definition, WritAspect.COST, stats.manaCost(), true);
        int cooldown = Math.max(0, scaledInt(caster, definition, WritAspect.COOLDOWN, stats.cooldownTicks(), false));
        int duration = Math.max(0, scaledInt(caster, definition, WritAspect.DURATION, stats.durationTicks(), false));
        float damage = scaledFloat(caster, definition, WritAspect.POTENCY, stats.damage());
        float size = Math.max(0.05F, Math.abs(scaledFloat(caster, definition, WritAspect.REACH, stats.size())));
        if (mana == stats.manaCost() && cooldown == stats.cooldownTicks() && duration == stats.durationTicks()
                && damage == stats.damage() && size == stats.size()) {
            return stats;
        }
        return new MagicSkillResolvedStats(definition, stats.tuning(), damage, stats.speed(), size, mana, cooldown,
                duration, stats.knockback(), stats.barrierRestore(), stats.costScale());
    }

    /**
     * True when no writ will let this caster cast this spell at all.
     *
     * <p>This is anti-magic, and it is not a skill - it is MANIFESTATION set to ZERO, one cell of an
     * ordinary grammar, sitting in the same menu as raising a price. That is what makes it
     * frightening rather than a button: the wielder chose it out of a hundred other things.
     */
    public static boolean silenced(ServerPlayer caster, MagicSkillDefinition definition) {
        return definition != null && !MagicContent.isAuthoritySkill(definition.id())
                && ruling(caster, definition, WritAspect.MANIFESTATION) == WritOperation.ZERO;
    }

    /**
     * The operation ruling this aspect of this spell for this caster, or null when nothing does -
     * either because no writ names it, or because two disagreed and cancelled each other out.
     */
    public static WritOperation ruling(ServerPlayer caster, MagicSkillDefinition definition, WritAspect aspect) {
        if (caster == null || definition == null || caster.getServer() == null) {
            return null;
        }
        WritOperation agreed = null;
        for (ServerPlayer wielder : caster.getServer().getPlayerList().getPlayers()) {
            PlayerMagicState book = wielder.getData(MagicalAttachments.MAGIC_STATE);
            if (book == null) {
                continue;
            }
            for (Writ writ : book.manaLedger().writs()) {
                if (writ.aspect() != aspect || !writ.covers(definition)
                        || !writ.binds(wielder.getUUID(), caster.getUUID())) {
                    continue;
                }
                if (agreed == null) {
                    agreed = writ.operation();
                } else if (agreed != writ.operation()) {
                    return null;
                }
            }
        }
        return agreed;
    }

    /**
     * @param signed whether the result is allowed below zero. A cost may be: INVERT on a price is
     *               mana handed back, which is the point of legislating a price rather than raising
     *               one. A clock may not - a negative cooldown is not a shorter one.
     */
    private static int scaledInt(ServerPlayer caster, MagicSkillDefinition definition, WritAspect aspect, int base,
                                 boolean signed) {
        WritOperation ruling = ruling(caster, definition, aspect);
        if (ruling == null || ruling == WritOperation.LOCK) {
            return base;
        }
        int scaled = Math.round(base * ruling.factor());
        return signed ? scaled : Math.abs(scaled);
    }

    private static float scaledFloat(ServerPlayer caster, MagicSkillDefinition definition, WritAspect aspect,
                                     float base) {
        WritOperation ruling = ruling(caster, definition, aspect);
        if (ruling == null || ruling == WritOperation.LOCK) {
            return base;
        }
        return base * ruling.factor();
    }
}
