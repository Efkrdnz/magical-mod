package com.efkrdnz.magical.magic.mind;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Held on an element of the wielder's own scene: every viewer perceiving it at {@link #PIVOT} or above
 * is pushed up a step a tick, and every one below it down a step - protesting too much. It is how a
 * scene is pushed over into real, and it is useless on an audience that already doubts.
 */
public final class Insist {
    public static final float PIVOT = 0.3F;
    public static final float STEP = 0.01F;
    public static final int BASE_MANA = 3;
    private static final int SYNC_TICKS = 5;
    private static final int NOTICE_TICKS = 20;
    /** How far past the first real block the ray is carried, so a real cluster's own face is still met. */
    private static final double PAST_THE_WALL = 0.5;

    private Insist() {}

    public static float push(float belief) {
        return belief >= PIVOT ? STEP : -STEP;
    }

    /** One tick of a held Insist; true when it found an element and paid for it. */
    public static boolean tick(ServerPlayer player, PlayerMagicState state) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 far = eye.add(look.scale(MindService.UNVEIL_REACH));
        BlockHitResult wall = level.clip(new ClipContext(eye, far, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 to = wall.getType() == HitResult.Type.MISS ? far : wall.getLocation().add(look.scale(PAST_THE_WALL));
        // One Sight per scene, for this tick only: it finds each element's live box once (a figment by
        // id), serves the aim, and then answers who sees the element aimed at.
        MindService.Sight aimed = null;
        SceneAim.Hit best = null;
        for (LiveScene scene : MindService.scenesOf(player.getUUID())) {
            if (!scene.dimension().equals(level.dimension())) {
                continue;
            }
            MindService.Sight sight = new MindService.Sight(level, scene);
            SceneAim.Hit hit = scene.aim(eye, to, index -> sight.box(scene.elements().get(index)));
            if (hit != null && (best == null || hit.distance() < best.distance())) {
                best = hit;
                aimed = sight;
            }
        }
        if (aimed == null) {
            if (now % NOTICE_TICKS == 0) {
                player.displayClientMessage(Component.translatable("message.magical.insist_nothing"), true);
            }
            return false;
        }
        if (!MindService.payTick(player, state, MagicContent.INSIST, BASE_MANA)) {
            return false;
        }
        LiveScene scene = aimed.scene;
        LiveScene.Element element = scene.elements().get(best.index());
        for (LivingEntity viewer : aimed.viewers()) {
            int id = viewer.getId();
            if (scene.belief().shattered(id, element.index()) || !aimed.sees(viewer, element)) {
                continue;
            }
            scene.belief().nudge(id, element.index(), push(scene.belief().get(id, element.index())));
        }
        if (now % SYNC_TICKS == 0) {
            state.sync(player);
        }
        return true;
    }
}
