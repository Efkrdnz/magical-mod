package com.efkrdnz.magical.client;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.sword.SwordArrayEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.sword.Bind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Carries this client's own player along the Keel while they are riding it.
 *
 * <p><b>The server cannot do this and deliberately does not try.</b> A ride is a velocity, and a
 * velocity written onto a server-side player goes nowhere: vanilla sends the motion packet for
 * {@code hasImpulse} over {@code broadcast}, and {@code ChunkMap.TrackedEntity.updatePlayer} never
 * puts a player in their own audience. The one channel that would reach them, {@code hurtMarked},
 * replaces the client's velocity with the server's copy, and the server has no real copy of a
 * player's velocity to send. {@code TheKeelSkill} therefore contains no {@code setDeltaMovement}
 * at all and {@code SwordKeelGameTests.theServerNeverPushesTheRider} fails the moment one appears;
 * this is the other half, and {@code client/SpaceLawClient} is the precedent it is written from.
 *
 * <p>{@code PlayerTickEvent.Pre} fires at the top of {@code Player.tick()}, before the player's own
 * physics and input run, so the ride sets the velocity the rest of the tick works against rather
 * than fighting over the result. The server keeps everything else: the Edge clock, the mana every
 * ten ticks, the leash, the fall-damage exemption and the frame's authoritative origin.
 *
 * <p><b>Why the Array entity and not a flag on the state.</b> Nothing in {@code PlayerMagicState}
 * says a wielder is riding - the bind lives in {@code SwordService}'s per-wielder map, which is
 * server-only - but it is already on the wire as the {@code Bind} byte of the one
 * {@link SwordArrayEntity} every wielder has, because the renderer needs it. While {@code RIDDEN}
 * that entity sits on the wielder's own body centre, so finding it is a query over a box a few
 * blocks wide, and once found its id is cached and {@code getEntity} is a map lookup. The scale it
 * carries is read here too and handed to the HUD, so the draw arc costs no second query.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class SwordKeelClient {

    /**
     * How far out to look for the wielder's own Array entity.
     *
     * <p>Wider than a ride needs, because the same cache answers the HUD's draw arc and a frame
     * that has been set down or bound is off the body: {@code SwordService.KEEL_LEASH} is 24 and
     * {@code BIND_BREAK} is 40, so nothing legal is further away than this.
     */
    private static final double SEARCH = 48.0D;

    /** Ticks between rescans while no Array entity can be found, so a miss is not a query a tick. */
    private static final int RESCAN_INTERVAL = 20;

    private static int arrayId = -1;
    private static long nextScanTick = Long.MIN_VALUE;
    private static float scale = 1.0F;
    private static int presentMask;

    private SwordKeelClient() {}

    /**
     * The frame scale of this client's own Array, or 1 while there is nothing to read.
     *
     * <p>Refreshed by the tick below, which runs before the HUD's, so the arc and the blades agree
     * within a frame.
     */
    public static float frameScale() {
        return scale;
    }

    /**
     * How many swords are with this client's wielder, or {@code whole} while there is nothing to
     * read.
     *
     * <p>Off the formation entity's present mask, which it was already syncing for the renderer -
     * so the HUD's count costs nothing on the wire. Falling back to the full complement rather
     * than to zero matters for one frame each way: the entity arrives a tick after the draw and
     * leaves a tick after the sheathe, and a ring that snapped to empty in between would read as
     * every sword having been spent at the instant they appeared.
     */
    public static int presentSwords(int whole) {
        return arrayId < 0 ? whole : Integer.bitCount(presentMask);
    }

    @SubscribeEvent
    public static void beforePlayerPhysics(PlayerTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || event.getEntity() != player || player.isSpectator()) {
            return;
        }
        SwordArrayEntity array = localArray(player, level);
        scale = array == null ? 1.0F : Math.max(0.0F, array.value());
        presentMask = array == null ? 0 : array.presentMask();
        if (array == null || bind(array) != Bind.RIDDEN) {
            return;
        }
        // Gravity is already off - the server set it on the mount and it is synced - so the only
        // thing left is the heading. A ride is a straight line at a fixed speed, which is the
        // whole of its counterplay, so nothing here reads input but the look and the key.
        double speed = keyHeld() ? MagicContent.THE_KEEL.baseSpeed() : 0.0D;
        Vec3 heading = player.getLookAngle().scale(speed);
        player.setDeltaMovement(heading);
        // The server hands out forty ticks of grace after a ride, but the client collects fall
        // damage of its own from whatever was standing when the mount happened.
        player.fallDistance = 0.0F;
    }

    /** True while a loadout slot carrying The Keel has its key down. */
    private static boolean keyHeld() {
        for (int slot = 0; slot < MagicalKeyMappings.CAST_SLOTS.length; slot++) {
            if (MagicContent.THE_KEEL.id().equals(ClientMagicState.get().equippedSkill(slot))
                    && MagicalKeyMappings.CAST_SLOTS[slot].isDown()) {
                return true;
            }
        }
        return false;
    }

    /** What the Array's one compound says it is hanging off, or {@code HELD} if it says nothing. */
    private static Bind bind(SwordArrayEntity array) {
        CompoundTag data = array.syncedData();
        if (data == null) {
            return Bind.HELD;
        }
        int ordinal = data.getByte(SwordArrayEntity.TAG_BIND);
        Bind[] values = Bind.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Bind.HELD;
    }

    /**
     * This client's own Array entity, cached by id.
     *
     * <p>Gated on the wielder having their steel out, so a player who never found the chain -
     * which is almost everyone - never runs the query at all, and a failed scan waits
     * {@link #RESCAN_INTERVAL} rather than repeating every tick. The gate is exact rather than
     * conservative now: off means gone, so a sheathed wielder has no entity to find and the scan
     * would be looking for something that provably is not there.
     */
    private static SwordArrayEntity localArray(LocalPlayer player, ClientLevel level) {
        if (!ClientMagicState.get().swordArray().drawn()) {
            arrayId = -1;
            return null;
        }
        if (arrayId >= 0 && level.getEntity(arrayId) instanceof SwordArrayEntity cached
                && cached.isAlive() && cached.livingTarget() == player) {
            return cached;
        }
        arrayId = -1;
        long now = level.getGameTime();
        if (now < nextScanTick) {
            return null;
        }
        nextScanTick = now + RESCAN_INTERVAL;
        for (SwordArrayEntity found : level.getEntitiesOfClass(SwordArrayEntity.class,
                player.getBoundingBox().inflate(SEARCH))) {
            if (found.livingTarget() == player) {
                arrayId = found.getId();
                return found;
            }
        }
        return null;
    }
}
