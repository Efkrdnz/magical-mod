package com.efkrdnz.magical.magic.sword;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Array bound to an actual level, and <b>the only class in this package that knows what a
 * {@code Level} is</b>.
 *
 * <p>Everything that decides anything lives in {@link SwordArray}, {@link Projection} and
 * {@link SwordMath}, which are arithmetic and are pinned on exact numbers with no world under
 * them. This class only answers questions about the world, and it answers them in a <b>stable
 * order</b> - entity id ascending - because the shed is farthest-and-heaviest-first with a
 * tie-break, and a sweep that reshuffled the bodies it found every tick would make a deterministic
 * rule look like a random one. Same discipline, and the same reason, as {@code LevelPileWorld}.
 *
 * <p>One of these per wielder per dimension, held by {@link SwordService} and thrown away with the
 * rest of the live half.
 */
public final class LevelSwordWorld implements SwordWorld {

    private final ServerLevel level;

    public LevelSwordWorld(ServerLevel level) {
        this.level = level;
    }

    public ServerLevel level() {
        return level;
    }

    @Override
    public boolean bodyAlive(int entityId) {
        Entity body = level.getEntity(entityId);
        return body != null && body.isAlive();
    }

    /**
     * Whether a blade would be standing inside something.
     *
     * <p>The collision shape and not the render shape, so a blade may hang in a fence, a torch or
     * tall grass and may not hang in glass. An unloaded chunk answers <em>solid</em>: a station
     * out over terrain nobody is holding open is not a place to put metal.
     */
    @Override
    public boolean solidAt(double x, double y, double z) {
        BlockPos pos = BlockPos.containing(x, y, z);
        if (!level.isLoaded(pos)) {
            return true;
        }
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * The y of the first air over a sturdy face under this point, or {@code NaN}.
     *
     * <p>The same walk {@code VerseMatter.surfaceBelow} does, for the same reason: a point over
     * water, over a gap or over an unloaded chunk has no floor, and a caller that took the block
     * below on faith would plant a blade in the sea.
     */
    @Override
    public double surfaceBelow(double x, double y, double z, int searchBlocks) {
        BlockPos from = BlockPos.containing(x, y, z);
        for (int i = 0; i <= Math.max(0, searchBlocks); i++) {
            BlockPos pos = from.below(i);
            if (!level.isLoaded(pos)) {
                return Double.NaN;
            }
            if (!level.getBlockState(pos).isAir()) {
                continue;
            }
            BlockPos floor = pos.below();
            if (level.getBlockState(floor).isFaceSturdy(level, floor, Direction.UP)) {
                return pos.getY();
            }
        }
        return Double.NaN;
    }

    @Override
    public long now() {
        return level.getGameTime();
    }

    // ---- the stable-order sweeps --------------------------------------------------------------

    /**
     * Every living body in the box, <b>sorted by entity id ascending</b>.
     *
     * <p>Not on {@link SwordWorld}, because nothing in the pure core is allowed to enumerate the
     * world - the settle takes one {@code int strain} for exactly that reason. This is here for
     * the service's own sweeps: the line a shed blade cuts home, the bodies a risen blade meets.
     */
    public List<LivingEntity> bodiesIn(AABB box, Predicate<LivingEntity> filter) {
        List<LivingEntity> bodies = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box, filter));
        bodies.sort(Comparator.comparingInt(Entity::getId));
        return bodies;
    }

    /**
     * Every living body whose box the segment from {@code from} to {@code to} passes through, in
     * the same stable order, nothing further out than {@code slack} from the line.
     *
     * <p>This is the shed's cut and nothing else uses it. A shed is one line home drawn through
     * whatever is standing in it, and it has to be the same list on two consecutive ticks or a
     * blade would appear to pick a different victim mid-flight.
     */
    public List<LivingEntity> bodiesOnLine(Vec3 from, Vec3 to, double slack, Predicate<LivingEntity> filter) {
        AABB box = new AABB(from, to).inflate(slack);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity body : bodiesIn(box, filter)) {
            if (body.getBoundingBox().inflate(slack).clip(from, to).isPresent()) {
                out.add(body);
            }
        }
        return out;
    }
}
