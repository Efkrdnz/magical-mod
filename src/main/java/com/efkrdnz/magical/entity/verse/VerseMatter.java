package com.efkrdnz.magical.entity.verse;

import com.efkrdnz.magical.magic.incantation.Matter;
import com.efkrdnz.magical.magic.incantation.VersePrototype;
import com.efkrdnz.magical.magic.service.ConjuredTerrainService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The world work of a material body: the block each matter is, and the four shapes that lay it.
 * Every block goes through the conjured ledger and into {@link MatterKeeper} with the matter's
 * lifetime, so it comes back. A wake touches bodies and never a block; this touches blocks and
 * never a body, and the two families do not overlap.
 *
 * <p>A spray lays one block on the floor under its line. A flood fills the air over the floor
 * within its radius, and only air, and only where the block below is solid. A touch converts what
 * is already there within its radius, nearest first, up to {@link #TOUCH_CAP}: never air, never the
 * unbreakable, never a block with a block entity in it, never a light, never what it already is. A
 * mound heaps five blocks on the floor and one on top, into air only and never into a body's space.
 * Fire is real fire, placed only where fire can stand.
 */
public final class VerseMatter {

    public static final int TOUCH_CAP = 160;
    /** How far a spray, a flood or a mound looks down for the floor. */
    static final int FLOOR_SEARCH = 3;
    /** A flood hugs the ground: it looks for the surface from this far above its own block. */
    static final int FLOOD_RISE = 1;

    private VerseMatter() {
    }

    /** The block a matter is, here: fire is soul fire over soul soil. */
    public static BlockState placed(Matter matter, ServerLevel level, BlockPos pos) {
        return switch (matter) {
            case WATER -> Blocks.WATER.defaultBlockState();
            case LAVA -> Blocks.LAVA.defaultBlockState();
            case FLAME -> BaseFireBlock.getState(level, pos);
            case STONE -> Blocks.STONE.defaultBlockState();
            case GLASS -> Blocks.GLASS.defaultBlockState();
            case ICE -> Blocks.ICE.defaultBlockState();
            case EARTH -> Blocks.DIRT.defaultBlockState();
        };
    }

    /** Whether {@code state} is still the matter that was laid: fire of either kind, water at any level. */
    public static boolean isMatter(Matter matter, BlockState state) {
        return switch (matter) {
            case WATER -> state.is(Blocks.WATER);
            case LAVA -> state.is(Blocks.LAVA);
            case FLAME -> state.getBlock() instanceof BaseFireBlock;
            case STONE -> state.is(Blocks.STONE);
            case GLASS -> state.is(Blocks.GLASS);
            case ICE -> state.is(Blocks.ICE);
            case EARTH -> state.is(Blocks.DIRT);
        };
    }

    /** What a standing body lays on its first tick: a flood or a touch over its radius. */
    public static void lay(ServerLevel level, VersePrototype prototype, Vec3 at) {
        switch (prototype.shape()) {
            case FLOOD -> flood(level, prototype.matter(), at, prototype.radius());
            case TOUCH -> touch(level, prototype.matter(), at, prototype.radius());
            default -> { }
        }
    }

    /** One block on the floor under a flying spray. */
    public static void spray(ServerLevel level, Matter matter, Vec3 at) {
        BlockPos surface = surfaceBelow(level, BlockPos.containing(at), FLOOR_SEARCH);
        if (surface == null) {
            return;
        }
        ConjuredTerrainService.Edit edit = ConjuredTerrainService.begin(level);
        BlockState state = placed(matter, level, surface);
        if (state.canSurvive(level, surface)) {
            ConjuredTerrainService.replace(level, edit, surface, state);
        }
        MatterKeeper.keep(level, edit, matter, matter.lifetimeTicks());
    }

    /** The air over the floor within the radius, filled. */
    static void flood(ServerLevel level, Matter matter, Vec3 centre, float radius) {
        BlockPos origin = BlockPos.containing(centre);
        int reach = Mth.ceil(radius);
        ConjuredTerrainService.Edit edit = ConjuredTerrainService.begin(level);
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                if (dx * dx + dz * dz > radius * radius) {
                    continue;
                }
                BlockPos surface = surfaceBelow(level, origin.offset(dx, FLOOD_RISE, dz), FLOOR_SEARCH + FLOOD_RISE);
                if (surface == null) {
                    continue;
                }
                BlockState state = placed(matter, level, surface);
                if (state.canSurvive(level, surface)) {
                    ConjuredTerrainService.replace(level, edit, surface, state);
                }
            }
        }
        MatterKeeper.keep(level, edit, matter, matter.lifetimeTicks());
    }

    /** What is already there within the radius, converted, nearest first, up to the cap. */
    static void touch(ServerLevel level, Matter matter, Vec3 centre, float radius) {
        BlockPos origin = BlockPos.containing(centre);
        int reach = Mth.ceil(radius);
        List<BlockPos> targets = new ArrayList<>();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -reach; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }
                    BlockPos pos = origin.offset(dx, dy, dz);
                    if (convertible(level, pos, matter)) {
                        targets.add(pos);
                    }
                }
            }
        }
        targets.sort(Comparator.comparingDouble(pos -> pos.distSqr(origin)));
        ConjuredTerrainService.Edit edit = ConjuredTerrainService.begin(level);
        int converted = 0;
        for (BlockPos pos : targets) {
            if (converted >= TOUCH_CAP) {
                break;
            }
            if (ConjuredTerrainService.replace(level, edit, pos, placed(matter, level, pos))) {
                converted++;
            }
        }
        MatterKeeper.keep(level, edit, matter, matter.lifetimeTicks());
    }

    /** A heap where a clod landed: the floor block under it and its four neighbours, and one on top. */
    public static void mound(ServerLevel level, Matter matter, Vec3 at) {
        BlockPos base = surfaceBelow(level, BlockPos.containing(at), FLOOR_SEARCH);
        if (base == null) {
            return;
        }
        ConjuredTerrainService.Edit edit = ConjuredTerrainService.begin(level);
        for (BlockPos pos : new BlockPos[] {base, base.north(), base.south(), base.east(), base.west(), base.above()}) {
            if (level.getBlockState(pos).isAir() && clearOfBodies(level, pos)) {
                ConjuredTerrainService.replace(level, edit, pos, placed(matter, level, pos));
            }
        }
        MatterKeeper.keep(level, edit, matter, matter.lifetimeTicks());
    }

    /** Never air, never the unbreakable, never a block with a block entity, never a light, never already the matter. */
    static boolean convertible(ServerLevel level, BlockPos pos, Matter matter) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.hasBlockEntity() || state.is(Blocks.LIGHT)) {
            return false;
        }
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        return !isMatter(matter, state);
    }

    /** The first air block, walking down from {@code from} for {@code depth} blocks, whose floor is solid; null when there is none. */
    static BlockPos surfaceBelow(ServerLevel level, BlockPos from, int depth) {
        for (int i = 0; i <= depth; i++) {
            BlockPos pos = from.below(i);
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }
            BlockPos floor = pos.below();
            if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                return pos;
            }
        }
        return null;
    }

    private static boolean clearOfBodies(ServerLevel level, BlockPos pos) {
        return level.getEntitiesOfClass(LivingEntity.class, new AABB(pos), LivingEntity::isAlive).isEmpty();
    }
}
