package com.efkrdnz.magical.magic.visual;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.cast.MagicCastContent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLLoader;

/**
 * Profile registration + validation. The new roster registers its profiles through its skill
 * modules (MagicCastContent); the kept skills register theirs here. Runs at common setup.
 */
public final class MagicVisualContent {
    private static boolean initialized;

    private MagicVisualContent() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        init();
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        MagicContent.orderedSkillIds();
        MagicCastContent.init();
        MagicVisualContentKept.register();
        boolean strict = !FMLLoader.isProduction();
        try {
            VisualProfiles.validateAndReport(strict);
        } catch (IllegalStateException e) {
            MagicalMod.LOGGER.error("Visual profile validation failed", e);
            throw e;
        }
    }
}
