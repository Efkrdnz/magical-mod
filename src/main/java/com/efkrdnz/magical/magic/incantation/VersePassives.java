package com.efkrdnz.magical.magic.incantation;

import com.efkrdnz.magical.entity.SkillClashEffectEntity;
import com.efkrdnz.magical.entity.verse.VerseBodyEntity;
import com.efkrdnz.magical.entity.verse.VerseHitEffects;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.PlayerMagicState;
import com.efkrdnz.magical.magic.passive.ClassPassiveHandler;
import com.efkrdnz.magical.magic.service.ConjuredTerrainService;
import com.efkrdnz.magical.magic.service.SkillTargets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a written passive verse does, every tick, for as long as it is written anywhere in the
 * Grimoire ({@link PassiveVerses#written}). A Taper carries a light: a {@code minecraft:light}
 * block in the air of the eye position, moved through the conjured ledger every few ticks and
 * never placed into anything but air, so it lights the world the way a torch does and leaves
 * nothing behind. A Storm Taper adds a shock on every hostile in reach. A Halo turns every
 * projectile closing on the wielder within its radius - a vanilla one is deflected back the way it
 * came and becomes the wielder's, a verse body is broken - and drinks mana for each; a Half Halo
 * does the same further out but only within a sector of where the wielder looks. A Familiar
 * throws a needle at the nearest hostile in sight, from beside the shoulder, and drinks mana for
 * each. Paid per act and never per press, and nothing runs on an empty pool.
 */
public final class VersePassives implements ClassPassiveHandler {

    public static final int TAPER_LIGHT = 13;
    public static final int TAPER_INTERVAL = 5;
    public static final double HALO_RADIUS = 2.5D;
    public static final int HALO_COST = 3;
    public static final double HALF_HALO_RADIUS = 4.5D;
    /** Half the sector's opening: within this many degrees of the look, either side. */
    public static final double HALF_HALO_DEGREES = 55.0D;
    public static final int HALF_HALO_COST = 2;
    public static final double FAMILIAR_RANGE = 12.0D;
    public static final int FAMILIAR_INTERVAL = 30;
    public static final int FAMILIAR_COST = 1;
    /** The needle leaves from beside the right shoulder, a little below the eyes. */
    public static final double FAMILIAR_SIDE = 0.45D;
    public static final double FAMILIAR_DROP = 0.2D;
    public static final int STORM_INTERVAL = 20;
    public static final double STORM_RADIUS = 2.5D;
    /** The turn's flare: the counter clash at a third of its size, because it happens at the wielder's own elbow. */
    public static final float FLARE_SCALE = 0.22F;
    public static final int FLARE_LIFE = 12;

    private record Light(ServerLevel level, BlockPos pos, ConjuredTerrainService.Edit edit) {
    }

    private final Map<UUID, Light> lights = new HashMap<>();

    @Override
    public Set<ResourceLocation> handled() {
        return Set.of();
    }

    @Override
    public void tick(ServerPlayer player, PlayerMagicState state) {
        Set<ResourceLocation> written = PassiveVerses.written(state.grimoire(), VerseContent.CATALOGUE);
        boolean lit = written.contains(PassiveVerses.TAPER) || written.contains(PassiveVerses.STORM_TAPER);
        if (lit) {
            if (player.tickCount % TAPER_INTERVAL == 0) {
                carryLight(player);
            }
        } else {
            snuff(player.getUUID());
        }
        if (written.isEmpty()) {
            return;
        }
        if (written.contains(PassiveVerses.STORM_TAPER) && player.tickCount % STORM_INTERVAL == 0) {
            shock(player);
        }
        if (written.contains(PassiveVerses.HALO)) {
            turn(player, state, HALO_RADIUS, 180.0D, HALO_COST);
        }
        if (written.contains(PassiveVerses.HALF_HALO)) {
            turn(player, state, HALF_HALO_RADIUS, HALF_HALO_DEGREES, HALF_HALO_COST);
        }
        if (written.contains(PassiveVerses.FAMILIAR) && player.tickCount % FAMILIAR_INTERVAL == 0) {
            throwNeedle(player, state);
        }
    }

    @Override
    public void forget(UUID playerId) {
        snuff(playerId);
    }

    // ------------------------------------------------------------------ the tapers

    /** The light to the eye block, if it moved; into air only, and never twice into the same block. */
    private void carryLight(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        Light light = lights.get(player.getUUID());
        if (light != null && light.level() == level && light.pos().equals(eye)) {
            return;
        }
        snuff(player.getUUID());
        if (!level.getBlockState(eye).isAir()) {
            return;
        }
        ConjuredTerrainService.Edit edit = ConjuredTerrainService.begin(level);
        ConjuredTerrainService.replace(level, edit, eye, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, TAPER_LIGHT));
        lights.put(player.getUUID(), new Light(level, eye, edit));
    }

    /** The light out, wherever it was; a block a player has since put there is theirs. */
    private void snuff(UUID playerId) {
        Light light = lights.remove(playerId);
        if (light != null) {
            ConjuredTerrainService.restoreUnlessBuiltOver(light.level(), light.edit(), state -> state.is(Blocks.LIGHT));
        }
    }

    /** Where the wielder's light stands, for tests; null when it is out. */
    public BlockPos lightAt(UUID playerId) {
        Light light = lights.get(playerId);
        return light == null ? null : light.pos();
    }

    private static void shock(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (LivingEntity target : SkillTargets.hostilesWithin(level, player, player.position(), STORM_RADIUS)) {
            VerseHitEffects.apply(HitEffect.SHOCK, target, player, player.position(), level.random);
        }
    }

    // ------------------------------------------------------------------ the halos

    /** Every projectile closing on the wielder within the radius and the sector, turned, for the cost each. */
    private static void turn(ServerPlayer player, PlayerMagicState state, double radius, double halfAngle, int cost) {
        ServerLevel level = player.serverLevel();
        Vec3 centre = player.getBoundingBox().getCenter();
        Vec3 look = player.getLookAngle();
        AABB box = new AABB(centre, centre).inflate(radius);
        boolean turned = false;
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, box, p -> p.isAlive() && p.getOwner() != player)) {
            if (state.mana() < cost) {
                break;
            }
            Vec3 offset = projectile.position().subtract(centre);
            if (offset.lengthSqr() > radius * radius || !inSector(look, offset, halfAngle) || !closing(projectile.getDeltaMovement(), offset)) {
                continue;
            }
            if (!projectile.deflect(ProjectileDeflection.REVERSE, player, player, true)) {
                continue;
            }
            state.addMana(-cost);
            flare(level, projectile.position());
            turned = true;
        }
        for (VerseBodyEntity body : level.getEntitiesOfClass(VerseBodyEntity.class, box, b -> b.isAlive() && b.livingOwner() != player)) {
            if (state.mana() < cost) {
                break;
            }
            Vec3 offset = body.position().subtract(centre);
            if (offset.lengthSqr() > radius * radius || !inSector(look, offset, halfAngle) || !closing(body.direction(), offset)) {
                continue;
            }
            body.discard();
            state.addMana(-cost);
            flare(level, body.position());
            turned = true;
        }
        if (turned) {
            state.sync(player);
        }
    }

    /** Within {@code halfAngleDegrees} of the look, either side; 180 is everywhere. Pure. */
    public static boolean inSector(Vec3 look, Vec3 offset, double halfAngleDegrees) {
        if (halfAngleDegrees >= 180.0D || offset.lengthSqr() < 1.0E-8D || look.lengthSqr() < 1.0E-8D) {
            return true;
        }
        double cos = look.normalize().dot(offset.normalize());
        return cos >= Math.cos(Math.toRadians(halfAngleDegrees));
    }

    /** Moving toward the centre: some of its velocity runs against its offset from it. Pure. */
    public static boolean closing(Vec3 velocity, Vec3 offset) {
        return velocity.dot(offset) < 0.0D;
    }

    /** A spark where the turn happened, not a battle: a halo may turn an arrow every tick of a volley. */
    private static void flare(ServerLevel level, Vec3 at) {
        int color = MagicContent.GRIMOIRE.color();
        level.addFreshEntity(SkillClashEffectEntity.create(level, at, color, color, FLARE_LIFE, FLARE_SCALE));
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.6F, 1.4F);
    }

    // ------------------------------------------------------------------ the familiar

    private static void throwNeedle(ServerPlayer player, PlayerMagicState state) {
        if (state.mana() < FAMILIAR_COST) {
            return;
        }
        ServerLevel level = player.serverLevel();
        LivingEntity target = nearestSeen(level, player);
        if (target == null) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0.0D, look.x);
        side = side.lengthSqr() < 1.0E-6D ? new Vec3(1.0D, 0.0D, 0.0D) : side.normalize();
        Vec3 shoulder = player.getEyePosition().add(side.scale(FAMILIAR_SIDE)).add(0.0D, -FAMILIAR_DROP, 0.0D);
        Vec3 aim = target.getBoundingBox().getCenter().subtract(shoulder);
        ProjectilePlan needle = new ProjectilePlan(VersePrototypes.NEEDLE, PassiveVerses.FAMILIAR, new ShotState(), PayloadKind.NONE, 0, null);
        VerseBodyEntity.spawn(level, player, needle, shoulder, aim, MagicContent.GRIMOIRE.id());
        state.addMana(-FAMILIAR_COST);
        state.sync(player);
    }

    private static LivingEntity nearestSeen(ServerLevel level, ServerPlayer player) {
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity candidate : SkillTargets.hostilesWithin(level, player, player.position(), FAMILIAR_RANGE)) {
            if (!player.hasLineOfSight(candidate)) {
                continue;
            }
            double distance = candidate.distanceToSqr(player);
            if (distance < best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }
}
