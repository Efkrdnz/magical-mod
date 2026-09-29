package com.efkrdnz.magical.client.mind;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.ClientMagicState;
import com.efkrdnz.magical.client.BloodShapeInput;
import com.efkrdnz.magical.client.MagicWheelOverlay;
import com.efkrdnz.magical.client.SpaceManipulationOverlay;
import com.efkrdnz.magical.magic.AuthorityContent;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.mind.Belt;
import com.efkrdnz.magical.magic.mind.Brush;
import com.efkrdnz.magical.magic.mind.DraftRay;
import com.efkrdnz.magical.magic.mind.Figment;
import com.efkrdnz.magical.magic.mind.Impression;
import com.efkrdnz.magical.magic.mind.Lexicon;
import com.efkrdnz.magical.magic.mind.Offset;
import com.efkrdnz.magical.magic.mind.Reverie;
import com.efkrdnz.magical.magic.mind.ReverieNbt;
import com.efkrdnz.magical.network.DreamEditPayload;
import com.efkrdnz.magical.network.MagicalNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Daydream: the wielder draws their active reverie into the air around them with what they have
 * studied. A client-only draft; the server hears of it once, when drawing stops.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class DaydreamMode {
    public static final double REACH = 6.0;
    /** Farther than this from where drawing began, the draft is no longer around the wielder: it is put away. */
    public static final double STRAY_BLOCKS = 32.0;

    private static boolean active;
    /** Whether this session began in the wielder's own dream: fixed at {@link #begin}, so a dream starting or ending mid-draw ends the mode rather than changing what it does. */
    private static boolean inDream;
    private static Reverie draft;
    private static BlockPos anchor;
    private static int turns;
    private static int slot;
    /** The belt slot in hand, 0-8: the client's alone, as vanilla's hotbar selection is. */
    private static int selected;
    private static Brush brush = Brush.POINT;
    private static BlockPos corner;
    private static boolean dirty;
    private static Level drawnIn;
    private static Object drawnBy;

    private DaydreamMode() {}

    public static boolean active() {
        return active;
    }

    /** In your own dream Daydream writes real blocks through the server instead of a draft. */
    public static boolean dreaming() {
        return inDream;
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
        inDream = ClientDream.ownDream();
        draft = inDream ? new Reverie() : mind.active().copy();
        int facing = minecraft.player.getDirection().get2DDataValue();
        if (draft.isEmpty()) {
            draft.setFacing(facing);
        }
        turns = facing - draft.facing();
        anchor = minecraft.player.blockPosition();
        drawnIn = minecraft.level;
        drawnBy = minecraft.player;
        corner = null;
        dirty = false;
        active = true;
        BeltHotbarOverlay.selectionChanged();
    }

    public static void finish(boolean save) {
        if (active && save && dirty && !inDream) {
            MagicalNetwork.sendSaveReverie(slot, ReverieNbt.save(draft));
        }
        active = false;
        inDream = false;
        draft = null;
        corner = null;
        drawnIn = null;
        drawnBy = null;
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

    public static int selected() {
        return selected;
    }

    /** The lie in hand: the belt's key at {@link #selected}, or null for an empty slot, which places nothing. */
    public static String impression() {
        return ClientMagicState.get().mind().belt().get(selected);
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
        // A screen owns the wheel, and so does the loadout switcher: it is held with B while drawing,
        // and swapping loadout is the one thing the wheel is for then.
        if (!active || delta == 0.0 || Minecraft.getInstance().screen != null || MagicWheelOverlay.isVisible()) {
            return false;
        }
        if (Screen.hasAltDown()) {
            brush = brush.next();
            corner = null;
        } else if (!BeltHotbarOverlay.showing(Minecraft.getInstance())) {
            // A spectator's wheel belongs to the spectator menu, as the number keys do.
            return false;
        } else {
            // As vanilla's hotbar turns: the wheel up is the slot to the left, wrapping.
            selected = Math.floorMod(selected + (delta > 0 ? -1 : 1), Belt.SIZE);
            BeltHotbarOverlay.selectionChanged();
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
        if (dreaming()) {
            placeInDream(minecraft);
            return;
        }
        Impression chosen = Impression.parse(impression());
        DraftRay.Hit hit = cursor(minecraft);
        if (chosen == null || hit == null) {
            return;
        }
        Lexicon lexicon = ClientMagicState.get().mind().lexicon();
        Reverie.Refusal worst = Reverie.Refusal.NONE;
        boolean placed = false;
        if (chosen.kind() == Impression.Kind.CREATURE) {
            Reverie.Refusal refusal = draft.addFigment(offset(hit.place()), chosen.id(), lexicon);
            worst = note(worst, refusal);
            placed = refusal == Reverie.Refusal.NONE;
            corner = null;
        } else if (brush == Brush.POINT || corner != null) {
            BlockPos from = brush == Brush.POINT ? hit.place() : corner;
            for (Offset cell : brush.cells(offset(from), offset(hit.place()))) {
                Reverie.Refusal refusal = draft.addBlock(cell, chosen.id(), lexicon);
                worst = note(worst, refusal);
                placed |= refusal == Reverie.Refusal.NONE;
                if (refusal == Reverie.Refusal.FULL) {
                    break;
                }
            }
            corner = null;
        } else {
            corner = hit.place();
            return;
        }
        dirty |= placed;
        if (worst != Reverie.Refusal.NONE && worst != Reverie.Refusal.OCCUPIED && minecraft.player != null) {
            minecraft.player.displayClientMessage(Component.translatable("message.magical.reverie_refused",
                    Component.translatable("mind.magical.refusal." + worst.name().toLowerCase(Locale.ROOT))), true);
        }
    }

    private static void placeInDream(Minecraft minecraft) {
        DraftRay.Hit hit = cursor(minecraft);
        if (hit == null) {
            return;
        }
        // The sneak binding, whatever key it is on: a physical Shift check ignored a player who sneaks on Control.
        if (minecraft.options.keyShift.isDown()) {
            markFlaw(minecraft, hit);
            return;
        }
        String key = impression();
        Impression chosen = Impression.parse(key);
        if (chosen == null) {
            return;
        }
        if (chosen.kind() == Impression.Kind.CREATURE) {
            MagicalNetwork.sendDreamEdit(new DreamEditPayload(DreamEditPayload.PLACE, List.of(hit.place()), key, -1));
            corner = null;
            return;
        }
        if (brush != Brush.POINT && corner == null) {
            corner = hit.place();
            return;
        }
        BlockPos from = brush == Brush.POINT ? hit.place() : corner;
        List<BlockPos> cells = brush.cells(offset(from), offset(hit.place())).stream().map(DaydreamMode::world).toList();
        MagicalNetwork.sendDreamEdit(new DreamEditPayload(DreamEditPayload.PLACE, cells, key, -1));
        corner = null;
    }

    /**
     * The entity the cursor's own ray reaches before it reaches a block, out to {@link #REACH}.
     * Vanilla's crosshair entity stops at the interaction range, which is shorter, so a figment past
     * it would be missed and the block behind it taken instead.
     */
    private static Entity entityAlongRay(Minecraft minecraft, DraftRay.Hit hit) {
        Player player = minecraft.player;
        if (player == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0F).normalize().scale(REACH));
        AABB sweep = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        EntityHitResult found = ProjectileUtil.getEntityHitResult(player, eye, end, sweep,
                e -> !e.isSpectator() && e.isPickable() && e != player && !(e instanceof Player), REACH * REACH);
        if (found == null) {
            return null;
        }
        double blockAt = hit == null || hit.solid() == null ? Double.POSITIVE_INFINITY
                : new AABB(hit.solid()).clip(eye, end).map(eye::distanceTo).orElse(0.0);
        return eye.distanceTo(found.getLocation()) <= blockAt ? found.getEntity() : null;
    }

    private static void markFlaw(Minecraft minecraft, DraftRay.Hit hit) {
        Entity target = entityAlongRay(minecraft, hit);
        if (target != null) {
            MagicalNetwork.sendDreamEdit(new DreamEditPayload(DreamEditPayload.FLAW, List.of(), "", target.getId()));
        } else if (hit.solid() != null) {
            MagicalNetwork.sendDreamEdit(new DreamEditPayload(DreamEditPayload.FLAW, List.of(hit.solid()), "", -1));
        }
    }

    private static void eraseInDream(Minecraft minecraft) {
        DraftRay.Hit hit = cursor(minecraft);
        Entity target = entityAlongRay(minecraft, hit);
        if (target != null) {
            MagicalNetwork.sendDreamEdit(new DreamEditPayload(DreamEditPayload.ERASE, List.of(), "", target.getId()));
            return;
        }
        if (hit != null && hit.solid() != null) {
            MagicalNetwork.sendDreamEdit(new DreamEditPayload(DreamEditPayload.ERASE, List.of(hit.solid()), "", -1));
        }
    }

    private static Reverie.Refusal note(Reverie.Refusal worst, Reverie.Refusal next) {
        return next == Reverie.Refusal.NONE ? worst : next;
    }

    /** A figment as it is drawn, and so as it is aimed at: the box its creature stands in. */
    public static AABB figmentBox(Figment figment) {
        BlockPos at = world(figment.at());
        var size = EntityType.byString(figment.creatureId()).map(EntityType::getDimensions).orElse(null);
        double half = size == null ? 0.3 : size.width() / 2.0;
        double height = size == null ? 1.95 : size.height();
        return new AABB(at.getX() + 0.5 - half, at.getY(), at.getZ() + 0.5 - half, at.getX() + 0.5 + half, at.getY() + height, at.getZ() + 0.5 + half);
    }

    /** The drafted figment the crosshair is on, nearest first; {@code distance[0]} is how far along the ray. Null when none. */
    private static Figment figmentUnderCursor(Minecraft minecraft, Vec3 eye, Vec3 end, double[] distance) {
        Figment nearest = null;
        for (Figment figment : draft.figments()) {
            AABB box = figmentBox(figment);
            // A ray that starts inside the box has no entry face; the box is then the nearest thing there is.
            double along = box.contains(eye) ? 0.0 : box.clip(eye, end).map(eye::distanceTo).orElse(Double.NaN);
            if (!Double.isNaN(along) && (nearest == null || along < distance[0])) {
                nearest = figment;
                distance[0] = along;
            }
        }
        return nearest;
    }

    /** Erases whatever the crosshair reaches first, a drafted figment by its drawn box or a drafted block by its cell. */
    private static void erase(Minecraft minecraft) {
        if (dreaming()) {
            eraseInDream(minecraft);
            return;
        }
        DraftRay.Hit hit = cursor(minecraft);
        if (hit == null || minecraft.player == null) {
            return;
        }
        Vec3 eye = minecraft.player.getEyePosition();
        Vec3 end = eye.add(minecraft.player.getViewVector(1.0F).normalize().scale(REACH));
        double[] figmentAt = {Double.POSITIVE_INFINITY};
        Figment figment = figmentUnderCursor(minecraft, eye, end, figmentAt);
        if (figment != null) {
            double blockAt = hit.solid() == null ? Double.POSITIVE_INFINITY
                    : new AABB(hit.solid()).clip(eye, end).map(eye::distanceTo).orElse(0.0);
            if (figmentAt[0] <= blockAt) {
                dirty |= draft.remove(figment.at());
                return;
            }
        }
        boolean removed = hit.solid() != null && draft.remove(offset(hit.solid()));
        if (!removed) {
            removed = draft.remove(offset(hit.place()));
        }
        dirty |= removed;
    }

    private static boolean drawsDaydream() {
        for (int i = 0; i < MagicContent.LOADOUT_SIZE; i++) {
            if (MagicContent.DAYDREAM.id().equals(ClientMagicState.get().equippedSkill(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * While drawing, the number keys choose a belt slot rather than a hotbar slot, and the inventory
     * key opens the Playbill instead of the inventory. Both have to run before vanilla's
     * handleKeybinds, which is what would otherwise take them.
     */
    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        // The Space selector and an armed Blood shape claim the number row in MagicalHotbarGuard.
        if (BeltHotbarOverlay.showing(minecraft) && !SpaceManipulationOverlay.active() && !BloodShapeInput.armed()) {
            for (int i = 0; i < Belt.SIZE; i++) {
                if (minecraft.options.keyHotbarSlots[i].consumeClick()) {
                    while (minecraft.options.keyHotbarSlots[i].consumeClick()) {
                        // every queued press lands on the same slot
                    }
                    selected = i;
                    BeltHotbarOverlay.selectionChanged();
                }
            }
        }
        if (active && !inDream && minecraft.screen == null && minecraft.options.keyInventory.consumeClick()) {
            while (minecraft.options.keyInventory.consumeClick()) {
                // one press, one Playbill
            }
            com.efkrdnz.magical.client.screen.mind.PlaybillScreen.open();
        }
    }

    /**
     * Drawing ends, saving what was drawn, the moment it stops being the wielder's doing: Daydream
     * left the loadout, they died or crossed into another level, or they are so far from the
     * anchor that the draft would be drawn round somewhere they are not. A screen opening is not
     * an end - the Playbill hands its edits back into the draft - it only stops the clicks and the
     * wheel being taken.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!active) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null
                || !ClientMagicState.get().hasAuthority(AuthorityContent.MIND)) {
            finish(false);
            return;
        }
        boolean gone = !drawsDaydream()
                || ClientDream.ownDream() != inDream
                || !minecraft.player.isAlive()
                || minecraft.player != drawnBy
                || minecraft.level != drawnIn
                || (!dreaming() && minecraft.player.distanceToSqr(Vec3.atCenterOf(anchor)) > STRAY_BLOCKS * STRAY_BLOCKS);
        if (gone) {
            finish(true);
        }
    }
}
