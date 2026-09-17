package com.efkrdnz.magical.magic.chaos;

import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.service.SkillTargets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Pile bound to an actual level.
 *
 * <p>Everything unpredictable about the Authority of Chaos lives in {@link Pile}, which is
 * arithmetic. This class only answers questions about the world, and it answers them in a
 * <em>stable order</em> - fixed direction order for blocks, entity id for bodies - because ties in
 * a sandpile are broken by neighbour order, and a cascade that reshuffled its neighbours every tick
 * would stop being deterministic.
 */
public final class LevelPileWorld implements PileWorld {

    private final ServerLevel level;
    private final UUID owner;

    public LevelPileWorld(ServerLevel level, UUID owner) {
        this.level = level;
        this.owner = owner;
    }

    public ServerLevel level() {
        return level;
    }

    @Override
    public int capacity(PileSite site) {
        if (site.isBlock()) {
            return level.isLoaded(site.block()) ? ChaosCapacity.ofBlock(level, site.block()) : 0;
        }
        Entity entity = level.getEntity(site.entityId());
        return entity == null ? 0 : ChaosCapacity.ofEntity(entity);
    }

    @Override
    public List<PileSite> neighbours(PileSite site) {
        List<PileSite> out = new ArrayList<>(8);
        if (site.isBlock()) {
            BlockPos pos = site.block();
            for (Direction direction : Direction.values()) {
                addBlock(out, pos.relative(direction));
            }
            addBodies(out, new AABB(pos).inflate(0.35D), null);
            return out;
        }
        Entity entity = level.getEntity(site.entityId());
        if (entity == null) {
            return List.of();
        }
        addBlock(out, entity.blockPosition().below());
        addBlock(out, entity.blockPosition());
        addBodies(out, entity.getBoundingBox().inflate(1.25D), entity);
        return out;
    }

    private void addBlock(List<PileSite> out, BlockPos pos) {
        if (level.isLoaded(pos) && !level.getBlockState(pos).isAir()) {
            out.add(PileSite.of(pos));
        }
    }

    /** Sorted by entity id, so the same crowd always produces the same neighbour order. */
    private void addBodies(List<PileSite> out, AABB box, Entity except) {
        List<LivingEntity> bodies = level.getEntitiesOfClass(LivingEntity.class, box, body -> body != except);
        bodies.sort(Comparator.comparingInt(Entity::getId));
        for (LivingEntity body : bodies) {
            out.add(PileSite.of(body.getId()));
        }
    }

    @Override
    public boolean living(PileSite site) {
        return !site.isBlock() && level.getEntity(site.entityId()) instanceof LivingEntity;
    }

    @Override
    public double height(PileSite site) {
        if (site.isBlock()) {
            return site.block().getY();
        }
        Entity entity = level.getEntity(site.entityId());
        return entity == null ? Double.MAX_VALUE : entity.getY();
    }

    @Override
    public boolean present(PileSite site) {
        if (site.isBlock()) {
            return level.isLoaded(site.block());
        }
        Entity entity = level.getEntity(site.entityId());
        return entity != null && entity.isAlive();
    }

    /**
     * Stress leaving the system: spent as force and harm where it stands.
     *
     * <p>Note what is <em>not</em> here - any check for who the wielder is. The Pile is matter and
     * matter does not know who made it, so a wielder standing in their own collapse is hurt by it
     * exactly as much as anybody else.
     */
    @Override
    public void shed(PileSite site, int amount) {
        Vec3 centre = centreOf(site);
        if (centre == null) {
            return;
        }
        float damage = 1.5F * amount;
        double radius = 1.5D + amount * 0.3D;
        ServerPlayer wielder = level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(owner);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(centre, centre).inflate(radius))) {
            SkillTargets.hurt(level, wielder, victim, damage, MagicContent.LAST_GRAIN.id());
            SkillTargets.shove(victim, centre, 0.35D + amount * 0.05D, 0.25D);
        }
        level.sendParticles(ParticleTypes.WITCH, centre.x, centre.y + 0.4D, centre.z,
                12 + amount * 4, radius * 0.4D, 0.4D, radius * 0.4D, 0.02D);
        level.sendParticles(ParticleTypes.CRIT, centre.x, centre.y + 0.4D, centre.z,
                6 + amount * 2, radius * 0.3D, 0.3D, radius * 0.3D, 0.12D);
        level.playSound(null, BlockPos.containing(centre), SoundEvents.CALCITE_BREAK, SoundSource.PLAYERS,
                Math.min(1.0F, 0.35F + amount * 0.08F), 0.6F + Math.min(0.8F, amount * 0.05F));
    }

    /** Null once the thing is gone, which is also how a cascade stops caring about it. */
    public Vec3 centreOf(PileSite site) {
        if (site.isBlock()) {
            return Vec3.atCenterOf(site.block());
        }
        Entity entity = level.getEntity(site.entityId());
        return entity == null ? null : entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
    }
}
