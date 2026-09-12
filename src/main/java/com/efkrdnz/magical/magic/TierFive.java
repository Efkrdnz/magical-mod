package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;

/**
 * The apex rule, in one place: <em>tier five answers only to tier five</em>.
 *
 * <p>A tier five offensive skill is not stopped by an ordinary defence, and a tier five defence is
 * not opened by an ordinary attack. Ultimate Protection turns aside anything below the apex and
 * nothing at it; the counter window offers itself for an apex threat only to an apex answer.
 *
 * <p>"Tier five" is what the lang calls them and how the game reads. Numerically the registry stops
 * at four, so the test is {@code tier >= 4} - it lives here rather than being written out at each
 * site, because a rule spelled out in three places is a rule that will eventually mean three
 * different things.
 */
public final class TierFive {

    /** Lowest numeric tier the game presents as "Tier 5". */
    public static final int APEX_TIER = 4;

    private TierFive() {}

    public static boolean is(MagicSkillDefinition skill) {
        return skill != null && skill.tier() >= APEX_TIER;
    }

    public static boolean is(ResourceLocation skillId) {
        return skillId != null && is(MagicContent.get(skillId));
    }

    /**
     * The skill whose damage is being applied right now, or null between applications.
     *
     * <p>Server thread only, written and restored around a single {@code target.hurt} call, which
     * is synchronous - every listener that reads it runs inside that call.
     */
    private static ResourceLocation attributed;

    /**
     * Names the skill responsible for the damage about to be dealt, and returns what was named
     * before so the caller can put it back.
     *
     * <p>This exists because a {@link DamageSource} cannot carry a skill id and the mod's damage
     * path throws the id away: {@code SkillTargets.hurt} builds {@code indirectMagic(owner, owner)},
     * so both the direct entity and the cause are the caster and nothing downstream can tell a
     * tier one bolt from an ultimate. Every ward and every counter that wants to ask "what tier hit
     * me" was therefore asking a question the damage could not answer.
     *
     * <p>Save-and-restore rather than set-and-clear because a spell's damage can set off another
     * spell's damage inside the same call.
     */
    public static ResourceLocation beginAttribution(ResourceLocation skillId) {
        ResourceLocation previous = attributed;
        attributed = skillId;
        return previous;
    }

    /** Puts back whatever {@link #beginAttribution} displaced. Always from a finally block. */
    public static void endAttribution(ResourceLocation previous) {
        attributed = previous;
    }

    /**
     * The skill behind a damage source, when there is one.
     *
     * <p>Three ways to know. The damage funnel names it outright while it is applying it, which
     * covers every skill that hurts through {@code SkillTargets}. Failing that, a spell that
     * arrives as its own entity carries its id - either a {@link SpellEffectEntity} or one of the
     * bespoke threat entities, which name theirs through {@link CounterableSkillThreat}. Both the
     * direct entity and the causing entity are checked, because a projectile is the direct entity
     * while its owner is the cause.
     */
    public static ResourceLocation skillOf(DamageSource source) {
        if (source == null) {
            return null;
        }
        if (attributed != null) {
            return attributed;
        }
        ResourceLocation direct = skillOf(source.getDirectEntity());
        return direct != null ? direct : skillOf(source.getEntity());
    }

    private static ResourceLocation skillOf(Entity entity) {
        if (entity instanceof SpellEffectEntity spell) {
            return spell.skillId();
        }
        if (entity instanceof CounterableSkillThreat threat) {
            return threat.counterSkillId();
        }
        return null;
    }

    /**
     * Whether this damage goes through a raised Ultimate Protection.
     *
     * <p>Before this existed the only thing that pierced was Judgement, identified by its entity
     * class. That made the ward absolute against everything else - a player holding it was immune
     * to every other spell in the game, which is not a defence with counterplay, it is an off
     * switch. Now anything at the apex gets through, which is what makes an apex offence worth
     * owning and an apex defence worth timing.
     */
    public static boolean piercesProtection(DamageSource source) {
        if (source instanceof com.efkrdnz.magical.boss.unwaking.UnwakingDamageSource encounter) return encounter.attack().powerTier() >= APEX_TIER;
        return is(skillOf(source));
    }
}
