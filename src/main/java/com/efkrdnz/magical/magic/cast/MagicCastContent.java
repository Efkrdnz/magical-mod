package com.efkrdnz.magical.magic.cast;

import com.efkrdnz.magical.MagicalMod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

/** Registers every skill's cast handler (one file per school + kept + class + fusion). */
public final class MagicCastContent {
    private static boolean initialized;

    private MagicCastContent() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        init();
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        MagicCastContentKept.register();
        MagicCastContentArcane.register();
        MagicCastContentFire.register();
        MagicCastContentWater.register();
        MagicCastContentLight.register();
        MagicCastContentVoid.register();
        MagicCastContentSpatial.register();
        MagicCastContentClass.register();
        MagicCastContentFusion.register();
        MagicCastContentBlood.register();
        MagicCastContentDark.register();
        MagicalMod.LOGGER.info("Registered {} skill cast handlers", SkillCastRegistry.all().size());
    }
}
