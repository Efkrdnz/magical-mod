package com.efkrdnz.magical.entity.fx;

import com.efkrdnz.magical.magic.MagicSkillDefinition;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** skill id -> behaviour. Registered next to the cast handler. */
public final class SpellBehaviors {
    private static final Map<ResourceLocation, SpellBehavior> BEHAVIORS = new HashMap<>();
    private static final SpellBehavior NONE = entity -> { };

    private SpellBehaviors() {}

    public static void register(MagicSkillDefinition definition, SpellBehavior behavior) {
        BEHAVIORS.put(definition.id(), behavior);
    }

    public static SpellBehavior get(ResourceLocation id) {
        return BEHAVIORS.getOrDefault(id, NONE);
    }

    public static boolean has(ResourceLocation id) {
        return BEHAVIORS.containsKey(id);
    }
}
