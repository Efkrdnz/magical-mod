package com.efkrdnz.magical.magic.mind;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/** Worked Dreamscapes for captures; they write blocks directly, around the owner's arrival, in their own dream only. */
public final class DreamPresets {
    public static final List<String> NAMES = List.of("library");

    private DreamPresets() {}

    public static boolean build(ServerPlayer player, String name) {
        DreamSession session = DreamService.session(player.getUUID());
        ServerLevel dream = DreamService.dreamLevel(player.server);
        if (session == null || !session.own || dream == null || !"library".equals(name)) {
            return false;
        }
        Dreamscape scape = DreamService.dreamscape(dream, player.getUUID());
        Offset a = scape.arrival();
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                set(dream, scape, a, x, -1, z, Blocks.OAK_PLANKS.defaultBlockState());
                boolean wall = Math.abs(x) == 4 || Math.abs(z) == 4;
                for (int y = 0; y <= 2; y++) {
                    set(dream, scape, a, x, y, z, wall ? Blocks.BOOKSHELF.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                set(dream, scape, a, x, 3, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
            }
        }
        set(dream, scape, a, -3, 0, -3, Blocks.LANTERN.defaultBlockState());
        set(dream, scape, a, 3, 0, 3, Blocks.LANTERN.defaultBlockState());
        if (set(dream, scape, a, 4, 1, 0, Blocks.JACK_O_LANTERN.defaultBlockState())) {
            scape.markBlock(new Offset(a.dx() + 4, a.dy() + 1, a.dz()));
        }
        DreamPlots.of(dream).changed();
        DreamService.sendState(player);
        return true;
    }

    /** Writes one cell, skipping any that would fall outside the plot (an arrival near its edge); true if it was written. */
    private static boolean set(ServerLevel dream, Dreamscape scape, Offset a, int x, int y, int z, BlockState state) {
        BlockPos pos = DreamService.at(scape.plot(), new Offset(a.dx() + x, a.dy() + y, a.dz() + z));
        if (!DreamRules.inside(scape.plot(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
            return false;
        }
        dream.setBlock(pos, state, Block.UPDATE_ALL);
        return dream.getBlockState(pos).is(state.getBlock());
    }
}
