package com.efkrdnz.magical.registry;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.arcane.menu.ArcaneWorkbenchMenu;
import com.efkrdnz.magical.magic.menu.ClassSelectMenu;
import com.efkrdnz.magical.magic.menu.ClassTreeMenu;
import com.efkrdnz.magical.magic.menu.GreedVaultMenu;
import com.efkrdnz.magical.magic.menu.MagicPyramidMenu;
import com.efkrdnz.magical.magic.menu.SpaceWalkerMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MagicalMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MagicalMod.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ArcaneWorkbenchMenu>> ARCANE_WORKBENCH =
            MENUS.register("arcane_workbench", () -> new MenuType<>(ArcaneWorkbenchMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<MagicPyramidMenu>> MAGIC_PYRAMID =
            MENUS.register("magic_pyramid", () -> new MenuType<>(MagicPyramidMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<GreedVaultMenu>> GREED_VAULT =
            MENUS.register("greed_vault", () -> new MenuType<>(GreedVaultMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredHolder<MenuType<?>, MenuType<SpaceWalkerMenu>> SPACE_WALKER =
            MENUS.register("space_walker", () -> new MenuType<>(SpaceWalkerMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<ClassSelectMenu>> CLASS_SELECT =
            MENUS.register("class_select", () -> new MenuType<>(ClassSelectMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<MenuType<?>, MenuType<ClassTreeMenu>> CLASS_TREE =
            MENUS.register("class_tree", () -> new MenuType<>(ClassTreeMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));

    private MagicalMenus() {}

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
