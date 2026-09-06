package com.efkrdnz.magical.client;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.MagicContent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public final class MagicalKeyMappings {
    public static final String CATEGORY = "key.categories." + MagicalMod.MODID;
    public static final KeyMapping[] CAST_SLOTS = new KeyMapping[MagicContent.LOADOUT_SIZE];
    public static final KeyMapping OPEN_CODEX = create("open_codex", GLFW.GLFW_KEY_K);
    public static final KeyMapping OPEN_WHEEL = create("open_wheel", GLFW.GLFW_KEY_B);
    public static final KeyMapping CONFIRM_WHEEL = create("confirm_wheel", GLFW.GLFW_KEY_V);
    public static final KeyMapping REFILL_BARRIER = create("refill_barrier", GLFW.GLFW_KEY_R);

    static {
        CAST_SLOTS[0] = create("cast_slot_1", GLFW.GLFW_KEY_Z);
        CAST_SLOTS[1] = create("cast_slot_2", GLFW.GLFW_KEY_X);
        CAST_SLOTS[2] = create("cast_slot_3", GLFW.GLFW_KEY_C);
    }

    private MagicalKeyMappings() {}

    private static KeyMapping create(String name, int keyCode) {
        return new KeyMapping("key.magical." + name, KeyConflictContext.IN_GAME, KeyModifier.NONE, InputConstants.Type.KEYSYM, keyCode, CATEGORY);
    }
}
