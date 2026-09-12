package com.efkrdnz.magical.item;

import com.efkrdnz.magical.entity.TrainingDummyEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Places a {@link TrainingDummyEntity} on the clicked face. */
public final class TrainingDummyItem extends Item {

    public TrainingDummyItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        TrainingDummyEntity dummy = new TrainingDummyEntity(serverLevel);
        // Faces whoever placed it, so the first thing you see is its front and its readout.
        Direction facing = context.getHorizontalDirection().getOpposite();
        dummy.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, facing.toYRot(), 0.0F);
        dummy.setYHeadRot(facing.toYRot());
        serverLevel.addFreshEntity(dummy);
        serverLevel.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.9F, 1.1F);
        if (context.getPlayer() == null || !context.getPlayer().hasInfiniteMaterials()) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.magical.training_dummy.desc"));
    }
}
