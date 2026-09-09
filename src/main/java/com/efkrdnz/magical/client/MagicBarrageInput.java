package com.efkrdnz.magical.client;

import com.efkrdnz.magical.client.renderer.MagicBarrageFieldRenderer;
import com.efkrdnz.magical.magic.MagicBarrageService;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public final class MagicBarrageInput {
    private static final boolean[] WAS_DOWN = new boolean[MagicContent.LOADOUT_SIZE];
    private static final int[] CHARGE_TICKS = new int[MagicContent.LOADOUT_SIZE];
    private static int previewSlot = -1;
    private static float previewFade;

    private MagicBarrageInput() {}

    public static boolean tickSlot(Minecraft minecraft, int slot) {
        if (minecraft.player == null || slot < 0 || slot >= MagicContent.LOADOUT_SIZE) {
            return false;
        }
        ResourceLocation skillId = ClientMagicState.get().equippedSkill(slot);
        if (!MagicContent.CIRCLE_ARSENAL.id().equals(skillId)) {
            resetSlot(slot);
            return false;
        }
        boolean down = MagicalKeyMappings.CAST_SLOTS[slot].isDown();
        boolean active = ClientMagicState.get().activeMagicBarrageEntityId() > 0;
        if (active) {
            if (down && !WAS_DOWN[slot]) {
                MagicalNetwork.sendMagicBarrageHold(slot, false, 0);
                previewSlot = -1;
                CHARGE_TICKS[slot] = 0;
            }
            WAS_DOWN[slot] = down;
            return true;
        }
        if (down && !WAS_DOWN[slot]) {
            CHARGE_TICKS[slot] = 0;
            previewSlot = slot;
        } else if (down && !active) {
            CHARGE_TICKS[slot] = Math.min(MagicBarrageService.MAX_CHARGE_TICKS, CHARGE_TICKS[slot] + 1);
            previewSlot = slot;
        } else if (!down && WAS_DOWN[slot]) {
            if (!active) {
                MagicalNetwork.sendMagicBarrageHold(slot, true, CHARGE_TICKS[slot]);
            }
            CHARGE_TICKS[slot] = 0;
            previewSlot = -1;
        }
        WAS_DOWN[slot] = down && !active;
        return true;
    }

    public static void renderPreview(RenderLevelStageEvent event, Minecraft minecraft) {
        if (previewSlot < 0 || minecraft.player == null || minecraft.player.isRemoved()) {
            previewFade = Math.max(0.0F, previewFade - 0.12F);
            return;
        }
        previewFade = Math.min(1.0F, previewFade + 0.16F);
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float radius = MagicBarrageService.radiusForCharge(CHARGE_TICKS[previewSlot]);
        Vec3 cameraPosition = event.getCamera().getPosition();
        Vec3 playerPosition = minecraft.player.getPosition(partialTick);
        event.getPoseStack().pushPose();
        event.getPoseStack().translate(playerPosition.x - cameraPosition.x, playerPosition.y - cameraPosition.y + minecraft.player.getBbHeight() * 0.5D, playerPosition.z - cameraPosition.z);
        MultiBufferSource.BufferSource buffer = minecraft.renderBuffers().bufferSource();
        MagicBarrageFieldRenderer.renderPreview(event.getPoseStack(), buffer, radius, minecraft.player.tickCount + partialTick, previewFade);
        buffer.endBatch();
        event.getPoseStack().popPose();
    }

    private static void resetSlot(int slot) {
        if (slot >= 0 && slot < WAS_DOWN.length) {
            WAS_DOWN[slot] = false;
            CHARGE_TICKS[slot] = 0;
            if (previewSlot == slot) {
                previewSlot = -1;
            }
        }
    }

    /** Abandon any charge in progress. Called when the player switches loadout. */
    public static void cancel() {
        for (int slot = 0; slot < MagicContent.LOADOUT_SIZE; slot++) {
            resetSlot(slot);
        }
    }
}
