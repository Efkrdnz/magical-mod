package com.efkrdnz.magical.mixin;

import com.efkrdnz.magical.magic.mind.MindPathing;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The mod's one mixin. A walking mob asks this for the type of every cell its box would take; an
 * imagined block it believes answers first, so it routes round a wall that is not there and onto
 * a floor that is not there.
 */
@Mixin(WalkNodeEvaluator.class)
public abstract class WalkNodeEvaluatorMixin extends NodeEvaluator {
    @Inject(method = "getPathType(Lnet/minecraft/world/level/pathfinder/PathfindingContext;III)Lnet/minecraft/world/level/pathfinder/PathType;",
            at = @At("HEAD"), cancellable = true)
    private void magical$believedPathType(PathfindingContext context, int x, int y, int z,
                                          CallbackInfoReturnable<PathType> cir) {
        PathType believed = MindPathing.override(context, this.mob, x, y, z);
        if (believed != null) {
            cir.setReturnValue(believed);
        }
    }
}
