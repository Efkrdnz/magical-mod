package com.efkrdnz.magical.tower;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.tower.archetype.TowerArchetype;
import com.efkrdnz.magical.tower.archetype.TowerArchetypes;
import com.efkrdnz.magical.tower.instance.InstanceManager;
import com.efkrdnz.magical.tower.instance.PlotGrid;
import com.efkrdnz.magical.tower.instance.TowerInstance;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator commands for exercising the tower instance framework before any dungeon exists.
 *
 * <p>Deliberately a separate root from the player-facing {@code /magicaltower}. Everything here is
 * a development tool: it allocates plots, teleports across dimensions, and destroys blocks. None
 * of it is meant to survive into normal play, and none of it is reachable without operator
 * permission.
 *
 * <pre>
 * /towerdev archetypes            list what is registered
 * /towerdev enter &lt;archetype&gt;     allocate a plot and travel to it
 * /towerdev leave                 return to where you were, releasing the plot
 * /towerdev goto &lt;arch&gt; &lt;plot&gt;    travel to a specific plot without allocating
 * /towerdev list                  show every live instance
 * /towerdev release               release your instance without leaving
 * /towerdev wipe &lt;archetype&gt;      clear untracked leftovers in that dimension
 * </pre>
 */
public final class TowerDevCommands {
    /** Where each player stood before entering, so {@code leave} can put them back. */
    private static final Map<UUID, ReturnPoint> RETURN_POINTS = new ConcurrentHashMap<>();

    private record ReturnPoint(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot) {}

    private TowerDevCommands() {}

    @EventBusSubscriber(modid = MagicalMod.MODID)
    public static final class Handlers {
        private Handlers() {}

        @SubscribeEvent
        public static void onRegisterTowerDevCommands(RegisterCommandsEvent event) {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("towerdev")
                    .requires(source -> source.hasPermission(2))
                    .then(Commands.literal("archetypes").executes(context -> listArchetypes(context.getSource())))
                    .then(Commands.literal("list").executes(context -> listInstances(context.getSource())))
                    .then(Commands.literal("leave").executes(context -> leave(context.getSource())))
                    .then(Commands.literal("release").executes(context -> release(context.getSource())))
                    .then(Commands.literal("enter")
                            .then(archetypeArgument()
                                    .executes(context -> enter(context.getSource(),
                                            ResourceLocationArgument.getId(context, "archetype")))))
                    .then(Commands.literal("goto")
                            .then(archetypeArgument()
                                    .then(Commands.argument("plot", IntegerArgumentType.integer(0, PlotGrid.MAX_PLOTS - 1))
                                            .executes(context -> travelToPlot(context.getSource(),
                                                    ResourceLocationArgument.getId(context, "archetype"),
                                                    IntegerArgumentType.getInteger(context, "plot"))))))
                    .then(Commands.literal("wipe")
                            .then(archetypeArgument()
                                    .executes(context -> wipe(context.getSource(),
                                            ResourceLocationArgument.getId(context, "archetype")))));
            event.getDispatcher().register(root);
        }

        /**
         * Uses the ResourceLocation argument type rather than a plain string: Brigadier's unquoted
         * string type rejects the colon in "magical:tower_liminal", so a bare namespaced id would
         * fail to parse without quotes.
         */
        private static RequiredArgumentBuilder<CommandSourceStack, ResourceLocation> archetypeArgument() {
            return Commands.argument("archetype", ResourceLocationArgument.id())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                            TowerArchetypes.ids().stream().map(ResourceLocation::toString).toList(), builder));
        }
    }

    private static int listArchetypes(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal("Registered archetypes:").withStyle(ChatFormatting.GOLD), false);
        for (TowerArchetype archetype : TowerArchetypes.all()) {
            source.sendSuccess(() -> Component.literal("  " + archetype.id()
                            + "  dim=" + archetype.dimension().location()
                            + "  y=" + archetype.floorY()
                            + (archetype.isPlaceholder() ? "  (no generator yet)" : ""))
                    .withStyle(archetype.isPlaceholder() ? ChatFormatting.GRAY : ChatFormatting.AQUA), false);
        }
        return TowerArchetypes.all().size();
    }

    private static int listInstances(CommandSourceStack source) {
        if (InstanceManager.liveCount() == 0) {
            source.sendSuccess(() -> Component.literal("No live instances.").withStyle(ChatFormatting.GRAY), false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Live instances (" + InstanceManager.liveCount() + "):")
                .withStyle(ChatFormatting.GOLD), false);
        for (TowerInstance instance : InstanceManager.all()) {
            BlockPos origin = instance.origin();
            source.sendSuccess(() -> Component.literal("  " + instance.key()
                            + "  " + instance.status()
                            + "  floor=" + instance.floor()
                            + "  at " + origin.getX() + " " + origin.getY() + " " + origin.getZ())
                    .withStyle(ChatFormatting.AQUA), false);
        }
        return InstanceManager.liveCount();
    }

    private static int enter(CommandSourceStack source, ResourceLocation archetypeId) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Needs to be run by a player."));
            return 0;
        }
        TowerArchetype archetype = resolve(source, archetypeId);
        if (archetype == null) {
            return 0;
        }
        if (InstanceManager.ownedBy(player.getUUID()) != null) {
            source.sendFailure(Component.literal("You already own an instance. Use /towerdev leave first."));
            return 0;
        }

        TowerInstance instance = InstanceManager.allocate(source.getServer(), archetype, player.getUUID());
        if (instance == null) {
            source.sendFailure(Component.literal("Could not allocate a plot in " + archetype.id()
                    + " - its dimension may not be loaded. See the server log."));
            return 0;
        }

        rememberReturnPoint(player);
        if (!travel(source, player, archetype.dimension(), instance.entryPoint())) {
            InstanceManager.release(instance.key());
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Entered " + instance.key()).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int travelToPlot(CommandSourceStack source, ResourceLocation archetypeId, int plotIndex) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Needs to be run by a player."));
            return 0;
        }
        TowerArchetype archetype = resolve(source, archetypeId);
        if (archetype == null) {
            return 0;
        }
        rememberReturnPoint(player);
        BlockPos origin = PlotGrid.origin(plotIndex, archetype.floorY());
        if (!travel(source, player, archetype.dimension(), origin.above())) {
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Travelled to " + archetype.id() + " plot " + plotIndex
                + " at " + origin.getX() + " " + origin.getY() + " " + origin.getZ()), false);
        return 1;
    }

    private static int leave(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Needs to be run by a player."));
            return 0;
        }
        TowerInstance instance = InstanceManager.ownedBy(player.getUUID());
        if (instance != null) {
            InstanceManager.release(instance.key());
        }
        ReturnPoint point = RETURN_POINTS.remove(player.getUUID());
        if (point == null) {
            source.sendFailure(Component.literal("No recorded return point. Teleport out manually."));
            return 0;
        }
        ServerLevel level = source.getServer().getLevel(point.dimension());
        if (level == null) {
            source.sendFailure(Component.literal("Return dimension is gone: " + point.dimension().location()));
            return 0;
        }
        player.teleportTo(level, point.x(), point.y(), point.z(),
                EnumSet.noneOf(Relative.class), point.yRot(), point.xRot(), false);
        source.sendSuccess(() -> Component.literal("Returned. Plot will be wiped on the next sweep.")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int release(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Needs to be run by a player."));
            return 0;
        }
        TowerInstance instance = InstanceManager.ownedBy(player.getUUID());
        if (instance == null) {
            source.sendFailure(Component.literal("You do not own a live instance."));
            return 0;
        }
        InstanceManager.release(instance.key());
        source.sendSuccess(() -> Component.literal("Released " + instance.key()
                + ". It will be wiped on the next sweep."), false);
        return 1;
    }

    private static int wipe(CommandSourceStack source, ResourceLocation archetypeId) {
        TowerArchetype archetype = resolve(source, archetypeId);
        if (archetype == null) {
            return 0;
        }
        int wiped = InstanceManager.wipeOrphans(source.getServer(), archetype);
        source.sendSuccess(() -> Component.literal("Wiped " + wiped + " orphaned plot(s) in " + archetype.id())
                .withStyle(ChatFormatting.YELLOW), true);
        return wiped;
    }

    private static TowerArchetype resolve(CommandSourceStack source, ResourceLocation archetypeId) {
        TowerArchetype archetype = TowerArchetypes.get(archetypeId);
        if (archetype == null) {
            source.sendFailure(Component.literal("Unknown archetype: " + archetypeId
                    + ". Try /towerdev archetypes."));
        }
        return archetype;
    }

    private static void rememberReturnPoint(ServerPlayer player) {
        RETURN_POINTS.put(player.getUUID(), new ReturnPoint(
                player.level().dimension(),
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot()));
    }

    private static boolean travel(CommandSourceStack source, ServerPlayer player,
            ResourceKey<Level> dimension, BlockPos target) {
        ServerLevel level = source.getServer().getLevel(dimension);
        if (level == null) {
            source.sendFailure(Component.literal("Dimension not loaded: " + dimension.location()));
            return false;
        }
        player.teleportTo(level,
                target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D,
                EnumSet.noneOf(Relative.class), player.getYRot(), player.getXRot(), false);
        return true;
    }
}
