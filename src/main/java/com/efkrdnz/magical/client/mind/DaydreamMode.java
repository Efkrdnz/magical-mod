package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.mind.Brush;
import com.efkrdnz.magical.magic.mind.DraftRay;
import com.efkrdnz.magical.magic.mind.Impression;
import com.efkrdnz.magical.magic.mind.Lexicon;
import com.efkrdnz.magical.magic.mind.Offset;
import com.efkrdnz.magical.magic.mind.Reverie;
import com.efkrdnz.magical.magic.mind.ReverieNbt;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Daydream: the wielder draws their active reverie into the air around them with what they have
 * studied. A client-only draft; the server hears of it once, when drawing stops.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class DaydreamMode {
    public static final double REACH = 6.0;

    private static boolean active;
    private static Reverie draft;
    private static BlockPos anchor;
    private static int turns;
    private static int slot;
    private static int impression;
    private static Brush brush = Brush.POINT;
    private static BlockPos corner;
    private static boolean dirty;

    private DaydreamMode() {}

    public static boolean active() {
        return active;
    }

    public static void toggle(Minecraft minecraft) {
        if (active) {
            finish(true);
        } else {
            begin(minecraft);
        }
    }

    private static void begin(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        var mind = ClientMagicState.get().mind();
        slot = mind.activeSlot();
        draft = mind.active().copy();
        int facing = minecraft.player.getDirection().get2DDataValue();
        if (draft.isEmpty()) {
            draft.setFacing(facing);
        }
        turns = facing - draft.facing();
        anchor = minecraft.player.blockPosition();
        corner = null;
        dirty = false;
        impression = Math.min(impression, Math.max(0, keys().size() - 1));
        active = true;
    }

    public static void finish(boolean save) {
        if (active && save && dirty) {
            MagicalNetwork.sendSaveReverie(slot, ReverieNbt.save(draft));
        }
        active = false;
        draft = null;
        corner = null;
    }

    public static Reverie draft() { return draft; }
    public static int slot() { return slot; }
    public static Brush brush() { return brush; }
    public static BlockPos corner() { return corner; }

    /** The Playbill hands its edits back through here, so leaving Daydream saves them too. */
    public static void setDraft(Reverie edited) {
        if (active && edited != null) {
            draft = edited.copy();
            dirty = true;
        }
    }

    public static List<String> keys() {
        return new ArrayList<>(ClientMagicState.get().mind().lexicon().keys());
    }

    public static String impression() {
        List<String> keys = keys();
        return keys.isEmpty() ? null : keys.get(Math.floorMod(impression, keys.size()));
    }

    public static BlockPos world(Offset offset) {
        Offset turned = offset.rotate(turns);
        return anchor.offset(turned.dx(), turned.dy(), turned.dz());
    }

    public static Offset offset(BlockPos pos) {
        return new Offset(pos.getX() - anchor.getX(), pos.getY() - anchor.getY(), pos.getZ() - anchor.getZ()).rotate(-turns);
    }

    public static DraftRay.Hit cursor(Minecraft minecraft) {
        if (!active || minecraft.player == null || minecraft.level == null) {
            return null;
        }
        return DraftRay.march(minecraft.player.getEyePosition(), minecraft.player.getViewVector(1.0F), REACH, pos ->
                !minecraft.level.getBlockState(pos).getCollisionShape(minecraft.level, pos).isEmpty()
                        || draft.blocks().stream().anyMatch(b -> b.at().equals(offset(pos))));
    }

    public static boolean handleScroll(double delta) {
        if (!active || delta == 0.0) {
            return false;
        }
        if (Screen.hasAltDown()) {
            brush = brush.next();
            corner = null;
        } else {
            impression += delta > 0 ? -1 : 1;
        }
        return true;
    }

    public static boolean handleMouseButton(int button, int action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!active || minecraft.screen != null || action != GLFW.GLFW_PRESS) {
            return false;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            place(minecraft);
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            erase(minecraft);
            return true;
        }
        return false;
    }

    private static void place(Minecraft minecraft) {
        Impression chosen = Impression.parse(impression());
        DraftRay.Hit hit = cursor(minecraft);
        if (chosen == null || hit == null) {
            return;
        }
        Lexicon lexicon = ClientMagicState.get().mind().lexicon();
        Reverie.Refusal worst = Reverie.Refusal.NONE;
        if (chosen.kind() == Impression.Kind.CREATURE) {
            worst = note(worst, draft.addFigment(offset(hit.place()), chosen.id(), lexicon));
        } else if (brush == Brush.POINT || corner != null) {
            BlockPos from = brush == Brush.POINT ? hit.place() : corner;
            for (Offset cell : brush.cells(offset(from), offset(hit.place()))) {
                Reverie.Refusal refusal = draft.addBlock(cell, chosen.id(), lexicon);
                worst = note(worst, refusal);
                if (refusal == Reverie.Refusal.FULL) {
                    break;
                }
            }
            corner = null;
        } else {
            corner = hit.place();
            return;
        }
        dirty = true;
        if (worst != Reverie.Refusal.NONE && worst != Reverie.Refusal.OCCUPIED && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("message.magical.reverie_refused",
                    Component.translatable("mind.magical.refusal." + worst.name().toLowerCase(Locale.ROOT))), true);
        }
    }

    private static Reverie.Refusal note(Reverie.Refusal worst, Reverie.Refusal next) {
        return worst == Reverie.Refusal.NONE ? next : worst;
    }

    private static void erase(Minecraft minecraft) {
        DraftRay.Hit hit = cursor(minecraft);
        if (hit == null) {
            return;
        }
        boolean removed = hit.solid() != null && draft.remove(offset(hit.solid()));
        if (!removed) {
            removed = draft.remove(offset(hit.place()));
        }
        dirty |= removed;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (active && (minecraft.player == null || !ClientMagicState.get().hasAuthority(AuthorityContent.MIND))) {
            finish(false);
        }
    }
}
