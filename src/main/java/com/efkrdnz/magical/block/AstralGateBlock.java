package com.efkrdnz.magical.block;

import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import com.efkrdnz.magical.tower.DungeonTowerService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class AstralGateBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;

    public AstralGateBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AstralGateBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        Direction facing = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.NORTH;
        int gateFloor = floorForMarker(serverLevel, pos, facing);
        if (serverPlayer.isShiftKeyDown()) {
            if (gateFloor > 0) {
                serverPlayer.displayClientMessage(Component.translatable("message.magical.gate_floor", gateFloor), false);
            }
            DungeonTowerService.status(serverPlayer);
            return InteractionResult.SUCCESS;
        }
        if (gateFloor > 0) {
            PlayerMagicState magicState = serverPlayer.getData(MagicalAttachments.MAGIC_STATE);
            int highestAvailable = Math.min(DungeonTowerService.MAIN_TOWER_FLOORS, magicState.towerClearedFloor(DungeonTowerService.MAIN_TOWER_ID) + 1);
            if (gateFloor > highestAvailable) {
                serverLevel.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), SoundSource.BLOCKS, 0.6F, 0.5F);
                serverPlayer.displayClientMessage(Component.translatable("message.magical.gate_floor_locked", gateFloor, highestAvailable), true);
                return InteractionResult.SUCCESS;
            }
        }
        serverLevel.sendParticles(ParticleTypes.REVERSE_PORTAL,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                48, 0.35D, 0.35D, 0.35D, 0.65D);
        serverLevel.playSound(null, pos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.7F, 0.55F);
        DungeonTowerService.enter(serverPlayer, gateFloor);
        return InteractionResult.SUCCESS;
    }

    /**
     * A gate is attuned by a wool marker placed two blocks behind it: directly behind its
     * facing, with a plain "two blocks south" fallback. Wool color maps to the floor,
     * bottom to top: blue, light blue, cyan, green, lime, yellow, orange, red.
     */
    private static int floorForMarker(Level level, BlockPos pos, Direction facing) {
        int floor = floorFromWool(level.getBlockState(pos.relative(facing.getOpposite(), 2)));
        if (floor == 0) {
            floor = floorFromWool(level.getBlockState(pos.relative(Direction.SOUTH, 2)));
        }
        return floor;
    }

    private static int floorFromWool(BlockState state) {
        if (state.is(Blocks.BLUE_WOOL)) {
            return 1;
        }
        if (state.is(Blocks.LIGHT_BLUE_WOOL)) {
            return 2;
        }
        if (state.is(Blocks.CYAN_WOOL)) {
            return 3;
        }
        if (state.is(Blocks.GREEN_WOOL)) {
            return 4;
        }
        if (state.is(Blocks.LIME_WOOL)) {
            return 5;
        }
        if (state.is(Blocks.YELLOW_WOOL)) {
            return 6;
        }
        if (state.is(Blocks.ORANGE_WOOL)) {
            return 7;
        }
        if (state.is(Blocks.RED_WOOL)) {
            return 8;
        }
        return 0;
    }
}
