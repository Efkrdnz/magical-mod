package com.efkrdnz.magical.magic.chaos;

import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.registry.MagicalAttachments;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Whose Pile is whose, and the tick that rolls the avalanche.
 *
 * <p><b>One Pile per wielder, and it does not survive.</b> Stress lives in memory and is dropped on
 * logout, on a change of dimension and on server stop - it is never written to the save. That is
 * thermodynamically apt (order decays) and it settles three problems at once: a Chaos wielder
 * cannot leave a permanent minefield on a shared server, the save format never has to learn what a
 * site is, and a Pile cannot grow without bound across sessions. What the wielder keeps is the
 * {@link Fracture}, which lives on {@code PlayerMagicState} and is the only part of this Authority
 * they actually own.
 */
public final class PileService {

    /** How often the wielder is told what they are looking at. */
    private static final int READOUT_INTERVAL = 5;

    /** How often stress is repainted for everyone who can see it. */
    private static final int MOTE_INTERVAL = 10;

    private static final int MOTE_SITE_CAP = 96;

    private record Held(ResourceKey<Level> dimension, Pile pile, LevelPileWorld world) {}

    private static final Map<UUID, Held> PILES = new HashMap<>();

    private PileService() {}

    /** The wielder Pile, rebuilt from nothing if they have changed dimension since it was made. */
    public static Pile pileFor(ServerPlayer player) {
        return held(player).pile();
    }

    public static LevelPileWorld worldFor(ServerPlayer player) {
        return held(player).world();
    }

    private static Held held(ServerPlayer player) {
        ResourceKey<Level> dimension = player.level().dimension();
        Held existing = PILES.get(player.getUUID());
        if (existing != null && existing.dimension().equals(dimension)) {
            return existing;
        }
        LevelPileWorld world = new LevelPileWorld(player.serverLevel(), player.getUUID());
        Held fresh = new Held(dimension, new Pile(world), world);
        PILES.put(player.getUUID(), fresh);
        return fresh;
    }

    /** Nothing of a Chaos wielder outlives them here. Called on logout and on dimension change. */
    public static void forget(UUID wielder) {
        PILES.remove(wielder);
    }

    public static void clear() {
        PILES.clear();
    }

    /** Rolls every settling avalanche by one tick of budget, and paints what is standing. */
    public static void tick(MinecraftServer server) {
        if (PILES.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Held held = PILES.get(player.getUUID());
            if (held == null || !held.dimension().equals(player.level().dimension())) {
                continue;
            }
            PlayerMagicState state = player.getData(MagicalAttachments.MAGIC_STATE);
            long now = player.serverLevel().getGameTime();
            held.pile().settle(state.fracture(), Pile.DEFAULT_BUDGET, now);
            if (now % MOTE_INTERVAL == 0) {
                paint(held, player.serverLevel());
            }
            if (now % READOUT_INTERVAL == 0) {
                readout(player, held);
            }
        }
    }

    /**
     * Stress is visible to everyone, which is the whole of the counterplay: break the block it is
     * sitting on, hit the body carrying it, or simply walk around the ground somebody prepared.
     */
    private static void paint(Held held, ServerLevel level) {
        int painted = 0;
        for (PileSite site : held.pile().sites()) {
            if (painted++ >= MOTE_SITE_CAP) {
                return;
            }
            Vec3 centre = held.world().centreOf(site);
            if (centre == null) {
                continue;
            }
            int motes = Math.min(3, held.pile().stressAt(site));
            level.sendParticles(ParticleTypes.WITCH, centre.x, centre.y + 0.55D, centre.z,
                    motes, 0.22D, 0.18D, 0.22D, 0.0D);
        }
    }

    /**
     * The one number that turns "nudge something" into "nudge <em>that</em>".
     *
     * <p>An actionbar line rather than a crosshair pip: a pip belongs on a HUD layer through
     * {@code HudBatch} and would have to move {@code HudSnapshotBudgetTest}, which is not worth
     * buying before the mechanic has been played.
     */
    private static void readout(ServerPlayer player, Held held) {
        PileSite aimed = ChaosAuthorityService.aimedSite(player, false);
        if (aimed == null) {
            return;
        }
        int stress = held.pile().stressAt(aimed);
        if (stress <= 0) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        int capacity = held.pile().capacityAt(aimed, now);
        boolean slack = held.pile().slack(aimed, now);
        player.displayClientMessage(Component.translatable(
                slack ? "message.magical.pile_slack" : "message.magical.pile_reading", stress, capacity), true);
    }
}
