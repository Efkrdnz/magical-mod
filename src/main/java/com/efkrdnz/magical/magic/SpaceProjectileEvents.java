package com.efkrdnz.magical.magic;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.entity.SpaceSubspaceEntity;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * Bounces projectiles off blocks while they are inside a subspace running
 * {@code RICOCHET_COLLISION} against projectiles.
 *
 * <p>This lives on the impact event rather than in the subspace rule loop because a block strike
 * resolves the projectile immediately — by the next tick there is nothing left for the loop to act
 * on.
 */
@EventBusSubscriber(modid = MagicalMod.MODID)
public final class SpaceProjectileEvents {
    private SpaceProjectileEvents() {}

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        HitResult hitResult = event.getRayTraceResult();
        if (!(projectile.level() instanceof ServerLevel level) || !(hitResult instanceof BlockHitResult blockHit)) {
            return;
        }

        // Bounded by the largest a subspace can be, so this stays cheap on a busy server. A
        // projectile inside a subspace is never further than that from its centre.
        AABB search = projectile.getBoundingBox().inflate(SpaceSubspaceEntity.MAX_RADIUS);
        List<SpaceSubspaceEntity> nearby = level.getEntitiesOfClass(
                SpaceSubspaceEntity.class, search, SpaceSubspaceEntity::hasProjectileBlockRicochet);

        for (SpaceSubspaceEntity subspace : nearby) {
            if (subspace.containsEntity(projectile)) {
                subspace.ricochetProjectileFromBlock(projectile, blockHit);
                event.setCanceled(true);
                return;
            }
        }
    }
}
