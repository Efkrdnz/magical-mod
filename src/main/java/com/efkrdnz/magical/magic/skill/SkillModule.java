package com.efkrdnz.magical.magic.skill;

import com.efkrdnz.magical.entity.fx.SpellBehavior;
import com.efkrdnz.magical.entity.fx.SpellBehaviors;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import com.efkrdnz.magical.magic.cast.SkillCastHandler;
import com.efkrdnz.magical.magic.cast.SkillCastRegistry;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.magic.visual.VisualProfiles;

/**
 * One class per skill: the cast handler, the entity behaviour and the visual profile live
 * together and are registered with a single call from the school's content file.
 */
public interface SkillModule {
    MagicSkillDefinition definition();

    SkillCastHandler handler();

    /** null when the skill spawns no SpellEffectEntity. */
    default SpellBehavior behavior() {
        return null;
    }

    VisualProfile.Builder profile();

    default void register() {
        MagicSkillDefinition definition = definition();
        SkillCastRegistry.register(definition, handler());
        SpellBehavior behavior = behavior();
        if (behavior != null) {
            SpellBehaviors.register(definition, behavior);
        }
        VisualProfiles.register(definition, profile());
    }
}
