package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.MagicSkillDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** skill id -> cast handler. Never an if-ladder; {@link #get} never returns null. */
public final class SkillCastRegistry {
    private static final Map<ResourceLocation, SkillCastHandler> HANDLERS = new LinkedHashMap<>();
    private static final SkillCastHandler MISSING = ctx -> {
        MagicalMod.LOGGER.warn("No cast handler registered for {}", ctx.definition().id());
        ctx.player().displayClientMessage(Component.translatable("message.magical.skill_locked"), true);
        return CastResult.FAILED;
    };

    private SkillCastRegistry() {}

    public static void register(MagicSkillDefinition definition, SkillCastHandler handler) {
        HANDLERS.put(definition.id(), handler);
    }

    public static boolean has(ResourceLocation id) {
        return HANDLERS.containsKey(id);
    }

    public static SkillCastHandler get(ResourceLocation id) {
        return HANDLERS.getOrDefault(id, MISSING);
    }

    public static Map<ResourceLocation, SkillCastHandler> all() {
        return java.util.Collections.unmodifiableMap(HANDLERS);
    }

    /** A handler whose slot press only prints the hold hint. */
    public static SkillCastHandler holdHint(String messageKey) {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                ctx.player().displayClientMessage(Component.translatable(messageKey), true);
                return CastResult.HANDLED;
            }

            @Override
            public boolean holdGated() {
                return true;
            }

            @Override
            public String holdHintKey() {
                return messageKey;
            }
        };
    }

    /** A handler that runs before mana/stat resolution and manages its own costs. */
    public static SkillCastHandler selfManaged(Consumer<CastContext> action) {
        return new SkillCastHandler() {
            @Override
            public CastResult cast(CastContext ctx) {
                action.accept(ctx);
                return CastResult.HANDLED;
            }

            @Override
            public boolean selfManaged() {
                return true;
            }
        };
    }
}
