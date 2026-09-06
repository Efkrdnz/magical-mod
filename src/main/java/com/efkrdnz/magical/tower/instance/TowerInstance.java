package com.efkrdnz.magical.tower.instance;

import com.efkrdnz.magical.tower.archetype.TowerArchetype;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * One live plot: a place a run is currently happening.
 *
 * <p>Deliberately a class and not a record. An instance is mutable by nature — its status changes,
 * players come and go, and its last-occupied time is stamped as it runs. A record would force a
 * new object on every heartbeat and could not be updated in place inside the manager's map.
 *
 * <p>Holds no gameplay logic. It is the bookkeeping the manager needs in order to know when a plot
 * can be reclaimed; what happens <em>inside</em> the dungeon belongs to the run layer above.
 */
public final class TowerInstance {
    /** Identity of a plot. Stable for as long as the instance is allocated. */
    public record Key(ResourceLocation archetypeId, int plotIndex) {
        @Override
        public String toString() {
            return archetypeId + "#" + plotIndex;
        }
    }

    /** Where an instance is in its life. Only ever moves forward. */
    public enum Status {
        /** Allocated and generated; a run may be in progress. */
        ACTIVE,
        /** Finished or abandoned. Awaiting the manager's next sweep to be wiped and reclaimed. */
        RELEASED
    }

    private final Key key;
    private final TowerArchetype archetype;
    private final UUID owner;
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    private final long createdGameTime;
    /**
     * Placements recorded during generation, stored as packed longs via {@link BlockPos#asLong()}.
     * This set is populated during generation and drained during release. Using packed longs instead
     * of BlockPos objects saves memory when a large dungeon places millions of blocks.
     */
    private final Set<Long> recordedPlacements = ConcurrentHashMap.newKeySet();

    private Status status = Status.ACTIVE;
    private int floor = 1;
    private long lastOccupiedGameTime;

    TowerInstance(Key key, TowerArchetype archetype, UUID owner, long gameTime) {
        this.key = key;
        this.archetype = archetype;
        this.owner = owner;
        this.createdGameTime = gameTime;
        this.lastOccupiedGameTime = gameTime;
        this.participants.add(owner);
    }

    public Key key() {
        return key;
    }

    public TowerArchetype archetype() {
        return archetype;
    }

    /** The player this plot was allocated for. Used for ownership and cleanup on logout. */
    public UUID owner() {
        return owner;
    }

    public Set<UUID> participants() {
        return participants;
    }

    public Status status() {
        return status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public int floor() {
        return floor;
    }

    public void setFloor(int floor) {
        this.floor = Math.max(1, floor);
    }

    public long createdGameTime() {
        return createdGameTime;
    }

    public long lastOccupiedGameTime() {
        return lastOccupiedGameTime;
    }

    /** Stamped whenever a participant is seen inside the plot, which is what defers expiry. */
    public void markOccupied(long gameTime) {
        this.lastOccupiedGameTime = gameTime;
    }

    /** Marks the plot done. The manager wipes and reclaims it on a later sweep. */
    public void release() {
        this.status = Status.RELEASED;
    }

    /** Records a block placement for later drainage. Called only during generation. */
    public void recordPlacement(BlockPos pos) {
        recordedPlacements.add(pos.asLong());
    }

    /** Returns the set of recorded placement positions (as packed longs). Drained during cleanup. */
    public Set<Long> recordedPlacements() {
        return recordedPlacements;
    }

    /** The plot's origin block, at its archetype's floor height. */
    public BlockPos origin() {
        return PlotGrid.origin(key.plotIndex(), archetype.floorY());
    }

    /** Where a player entering this instance should land. */
    public BlockPos entryPoint() {
        return archetype.generator().entryPoint(origin(), floor);
    }

    @Override
    public String toString() {
        return "TowerInstance[" + key + " floor=" + floor + " " + status + "]";
    }
}
