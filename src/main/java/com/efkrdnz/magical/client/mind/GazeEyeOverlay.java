package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.mind.GazeTracker;
import com.efkrdnz.magical.magic.mind.MindGazeService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The wielder's eye filling as they study: the name of what they are looking at under the
 * crosshair, the fidelity they already have as pips, and a hairline that fills to the next gaze.
 * Frameless - shadowed text and a one-pixel line, nothing behind them.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class GazeEyeOverlay {
    private static final int LILAC = 0xFFBDA4FF;
    private static final int TRACK = 0x66FFFFFF;
    private static final int BAR_WIDTH = 32;
    private static final int BELOW_CROSSHAIR = 10;
    private static final GazeTracker TRACKER = new GazeTracker();
    private static Vec3 last;

    private GazeEyeOverlay() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null
                || !ClientMagicState.get().hasAuthority(AuthorityContent.MIND)) {
            TRACKER.tick(null, false);
            last = null;
            return;
        }
        Vec3 now = minecraft.player.position();
        boolean still = last != null && last.distanceToSqr(now) < MindGazeService.STILL_SQR;
        last = now;
        HitResult hit = minecraft.hitResult;
        Entity entity = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        BlockState block = hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                ? minecraft.level.getBlockState(blockHit.getBlockPos()) : null;
        TRACKER.tick(MindGazeService.keyOf(entity, block), still);
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft) {
        String key = TRACKER.key();
        if (key == null || TRACKER.progress() <= 0.0F || minecraft.screen != null) {
            return;
        }
        int fidelity = ClientMagicState.get().mind().lexicon().fidelity(key);
        Component name = MindGazeService.displayName(key).copy()
                .append(Component.literal(" " + "◆".repeat(fidelity) + "◇".repeat(3 - fidelity)));
        int centreX = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() / 2 + BELOW_CROSSHAIR;
        graphics.drawString(minecraft.font, name, centreX - minecraft.font.width(name) / 2, y, LILAC, true);
        int barY = y + minecraft.font.lineHeight + 1;
        int left = centreX - BAR_WIDTH / 2;
        graphics.fill(left, barY, left + BAR_WIDTH, barY + 1, TRACK);
        graphics.fill(left, barY, left + Math.round(BAR_WIDTH * TRACKER.progress()), barY + 1, LILAC);
    }
}
