package com.efkrdnz.magical.forge;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.resources.ResourceLocation;

public final class ForgeIds {

    private ForgeIds() {}

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, path);
    }
}
