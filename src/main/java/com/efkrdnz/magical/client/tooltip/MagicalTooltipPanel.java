package com.efkrdnz.magical.client.tooltip;

import java.util.List;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.renderer.MagicalRenderTypes;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import org.joml.Matrix4f;
import org.joml.Vector2ic;

/**
 * The shader panel behind a magical weapon's tooltip, and the sprites vanilla draws over it.
 *
 * <p>There is no event after the tooltip background goes down, so the panel cannot simply be drawn
 * on top of it. What happens instead is a handshake between the two handlers here: {@code Texture}
 * points vanilla at our own background sprite, which is a dim near-black sheet rather than its
 * usual opaque one, and {@code Pre} paints the shader quad a pixel behind where that sheet will
 * land and flushes it immediately so the draw is already on the screen when vanilla arrives.
 *
 * <p>The sheet is dim rather than empty on purpose. If the shader ever fails to compile - a driver,
 * a resource pack, someone's shader mod - what is left is a dark panel and readable white text,
 * instead of words floating on the inventory behind them.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class MagicalTooltipPanel {

    /**
     * Resolves to the sprites {@code tooltip/magical_background} and {@code tooltip/magical_frame};
     * {@code RenderTooltipEvent.Texture} adds the folder and both suffixes itself.
     */
    private static final ResourceLocation SKIN =
            ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "magical");

    /** Just under vanilla's own 400, which is where the background and every line of text land. */
    private static final int PANEL_Z = 399;

    /**
     * How far vanilla's background reaches past the text box on every side.
     *
     * <p>{@code TooltipRenderUtil} blits from {@code x - 3 - 9} and its sprite's fill starts eight
     * pixels in, so the sheet covers {@code x - 4} outwards. The panel matches it exactly, which is
     * what puts the frame's one-pixel line just inside the panel's edge rather than across it.
     *
     * <p>Public because it is not really a choice made here - it is a measurement of
     * {@code magical_background.png}, and {@code MagicalTooltipAssetsTest} reads that file and
     * checks the two still agree. Redrawing the sprite without this failing would slide the panel
     * out from under its own frame.
     */
    public static final int BLEED = 4;

    /** Dense enough to read text off, sheer enough that the inventory still moves behind it. */
    private static final int PANEL_ALPHA = 224;

    private MagicalTooltipPanel() {}

    @SubscribeEvent
    public static void onTooltipTexture(RenderTooltipEvent.Texture event) {
        if (MagicalTooltipStyle.shows(event.getItemStack())) {
            event.setTexture(SKIN);
        }
    }

    /**
     * Last of all the {@code Pre} listeners, because {@code x}, {@code y} and the font are all
     * settable on this event and vanilla reads them after every listener has had its turn. Reading
     * them any earlier would put the panel where the tooltip was going to be before another mod
     * moved it.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTooltipPre(RenderTooltipEvent.Pre event) {
        ItemStack stack = event.getItemStack();
        if (!MagicalTooltipStyle.shows(stack)) {
            return;
        }
        Font font = event.getFont();
        List<ClientTooltipComponent> lines = event.getComponents();

        // Vanilla has not measured or placed the box yet when Pre fires - it does both a few lines
        // later, off this same event's font and position - so the measurement is repeated here.
        // The seed of -2 for a single-line tooltip is vanilla's, and dropping it would leave the
        // panel two pixels tall in the one case a player sees most often.
        int width = 0;
        int height = lines.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent line : lines) {
            width = Math.max(width, line.getWidth(font));
            height += line.getHeight(font);
        }
        Vector2ic at = event.getTooltipPositioner().positionTooltip(
                event.getScreenWidth(), event.getScreenHeight(), event.getX(), event.getY(), width, height);

        draw(event.getGraphics(), MagicalTooltipStyle.accent(stack),
                at.x() - BLEED, at.y() - BLEED, at.x() + width + BLEED, at.y() + height + BLEED);
    }

    /**
     * One quad, into the same buffer source {@code GuiGraphics} holds, then flushed on the spot.
     *
     * <p>The flush is the whole trick. Both draws go through one buffer source, so without it the
     * panel and vanilla's background would be batched and emitted in render-type order rather than
     * in call order, and which one ended up on top would stop being ours to decide.
     */
    private static void draw(GuiGraphics graphics, int accent, int x0, int y0, int x1, int y1) {
        VertexConsumer panel = Minecraft.getInstance().renderBuffers().bufferSource()
                .getBuffer(MagicalRenderTypes.magicalTooltip());
        Matrix4f pose = graphics.pose().last().pose();
        int red = accent >> 16 & 0xFF;
        int green = accent >> 8 & 0xFF;
        int blue = accent & 0xFF;
        corner(panel, pose, x0, y0, 0.0f, 0.0f, red, green, blue);
        corner(panel, pose, x0, y1, 0.0f, 1.0f, red, green, blue);
        corner(panel, pose, x1, y1, 1.0f, 1.0f, red, green, blue);
        corner(panel, pose, x1, y0, 1.0f, 0.0f, red, green, blue);
        graphics.flush();
    }

    /** UVs are the box's own 0..1, which is the only thing telling the shader where its edges are. */
    private static void corner(VertexConsumer panel, Matrix4f pose, int x, int y, float u, float v,
            int red, int green, int blue) {
        panel.addVertex(pose, x, y, PANEL_Z).setUv(u, v).setColor(red, green, blue, PANEL_ALPHA);
    }
}
