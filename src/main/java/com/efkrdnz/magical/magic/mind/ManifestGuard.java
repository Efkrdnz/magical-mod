package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

import java.util.List;

/**
 * A real block is only as real as the minds agreeing on it, and it stays where it was agreed on:
 * breaking one yields nothing, an explosion does not break it, a piston does not move it, and nothing
 * can be done to it by hand or by tool. Without this an imagined diamond wall is real diamonds. What
 * is guarded is a position an element's edit holds ({@link Manifestation#holds}), whatever stands
 * there now; a block a player places into a broken cell is theirs and leaves the edit.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class ManifestGuard {
    private ManifestGuard() {}

    private static boolean held(LevelAccessor level, BlockPos pos) {
        return MindService.anyLive() && level instanceof ServerLevel server && Manifestation.holds(server, pos);
    }

    @SubscribeEvent
    public static void onDrops(BlockDropsEvent event) {
        if (held(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (MindService.anyLive() && event.getLevel() instanceof ServerLevel level) {
            event.getAffectedBlocks().removeIf(pos -> Manifestation.holds(level, pos));
        }
    }

    /**
     * A block placed into a held cell: into a broken one it is the placer's, and the edit forgets the
     * cell so the element never takes it back; merged into imagined matter still standing (a slab
     * doubled, a candle added) it is refused, or the placer would own the imagined half too.
     */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!MindService.anyLive() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        List<BlockSnapshot> placed = event instanceof BlockEvent.EntityMultiPlaceEvent multi
                ? multi.getReplacedBlockSnapshots() : List.of(event.getBlockSnapshot());
        for (BlockSnapshot snapshot : placed) {
            if (Manifestation.holds(level, snapshot.getPos()) && standing(snapshot.getState())) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockSnapshot snapshot : placed) {
            Manifestation.forget(level, snapshot.getPos());
        }
    }

    /** Whether a held cell still has imagined matter in it rather than the air (or water) a break left. */
    private static boolean standing(BlockState replaced) {
        return !replaced.isAir() && replaced.getFluidState().isEmpty();
    }

    /** A real imagined block is inert: no door, trapdoor, button, cauldron, cake or bucket answers a hand. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (held(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    /** Nor a tool: no stripping, tilling, pathing, scraping or waxing. */
    @SubscribeEvent
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (held(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }

    /**
     * A piston that would move a held block, break one, or push a real block (or its own head) into a held cell (where
     * the element would take it back as air) does not fire. A plain piston pulls nothing back, so only
     * a sticky one is asked on the way in.
     */
    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (!MindService.anyLive() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (event.getPistonMoveType() == PistonEvent.PistonMoveType.RETRACT && !event.getState().is(Blocks.STICKY_PISTON)) {
            return;
        }
        // The head itself goes into the face cell, and would be taken back as air with the element.
        if (event.getPistonMoveType() == PistonEvent.PistonMoveType.EXTEND && Manifestation.holds(level, event.getFaceOffsetPos())) {
            event.setCanceled(true);
            return;
        }
        PistonStructureResolver structure = event.getStructureHelper();
        if (structure == null) {
            return;
        }
        // A failed resolve moves nothing, but the lists still say what it reached; asking them either
        // way costs nothing, since cancelling a move that was not going to happen changes nothing.
        structure.resolve();
        Direction push = structure.getPushDirection();
        for (BlockPos pos : structure.getToPush()) {
            if (Manifestation.holds(level, pos) || Manifestation.holds(level, pos.relative(push))) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos pos : structure.getToDestroy()) {
            if (Manifestation.holds(level, pos)) {
                event.setCanceled(true);
                return;
            }
        }
    }
}
