package com.efkrdnz.magical.magic.skill.eldritch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The deep as matter: what the school throws that is stuff rather than light.
 *
 * <p>The constructs are the user's models and the profiles draw the light round them - the
 * circles, the stain, the mouth, the halo. What neither drew was the ground a tentacle tears out
 * of or the ink it is made of, and a thing that erupts out of stone with no stone moving reads as
 * a thing that appeared. Everything here is a real particle sent at one of a skill's own beats -
 * an eruption, a squeeze, a snap, a ward giving way, a sting - never on a clock, so a construct
 * that is only standing there throws nothing.
 *
 * <p>The ink is the glow squid's, the deep's own teal: a squid's is a black square over daylight
 * stone, and a few of them where a golem stood read as a pile of black blocks. Either is a whole
 * block across (its quad is half a block each way), falls and has no collision, and within reach of
 * a camera it is a pixel square across the view: at four blocks one blot is a sixth of the frame's
 * height. So it is thrown only out of a hole in the ground or off a victim, and never within
 * {@value #INK_CLEARANCE} blocks of anybody's eyes -
 * which a lash on a body at arm's length, the deep's grasp erupting at a Noticed mage's feet or a
 * maw snapping under a player would otherwise put there. Up close the same ink goes out as flecks
 * of an ink sac instead, a tenth to a fifth of a block and falling, so the beat keeps its ink.
 */
final class EldritchMatter {
    /** No ink blot is thrown from a point this close to any player's eyes: flecks go in its place. */
    static final double INK_CLEARANCE = 4.0D;
    /** Ink-sac flecks thrown in place of each blot a close camera would have had across its view. */
    private static final int FLECKS_PER_BLOT = 3;
    /**
     * A fleck out of a hole: its lift and lean. A fleck falls at 0.04 a tick, so this lift peaks
     * about a block up and drops back into the break.
     */
    private static final double FLECK_LIFT = 0.28D;
    private static final double FLECK_LEAN = 0.06D;
    /** A fleck flung off a body: its speed along the spurt's line. */
    private static final double SPURT_FLECK_SPEED = 0.2D;
    /**
     * One pale plume of dust kicked out over the rim for this many pillars. A pillar is a crumb a
     * fifth of a block across, and eight blocks off on stone a ring of them is specks; the plumes
     * are what says the ground broke at that distance. Small puffs, not a POOF cloud.
     */
    private static final int PILLARS_PER_PLUME = 3;
    /** How fast a plume leans out over the rim; it lifts itself. */
    private static final double PLUME_SPREAD = 0.05D;
    /**
     * A stare: sculk pops keep 0.96 of their speed and live six to nine ticks, so one travels five
     * to eight times what it starts with - handed the length over seven, it ends on the body.
     */
    private static final double POP_TRAVEL = 7.0D;
    private static final double STARE_SPREAD = 0.12D;
    /** The souls lifting off a stung body, and how far across it they start. */
    private static final double SOUL_LIFT = 0.05D;
    private static final double SOUL_ACROSS = 0.35D;
    /** How many blocks down a beat looks for the floor it breaks: the one it stands on, and the next. */
    private static final int FLOOR_SEARCH = 2;
    /** Upward speed handed to a dust pillar; its provider rolls its own scatter round it. */
    private static final double PILLAR_LIFT = 0.2D;
    /**
     * Ink out of a hole: its lift and how far it leans out of the middle. Squid ink keeps 0.92 of
     * its speed a tick, so it travels about twelve times what it starts with - a block and a half.
     */
    private static final double INK_LIFT = 0.13D;
    private static final double INK_LEAN = 0.04D;
    /** Sculk popping in the break, and how fast it scatters. */
    private static final double POP_SCATTER = 0.02D;
    /** A spurt: the ink's speed along its line, the pops', and how far either strays off it. */
    private static final double SPURT_INK_SPEED = 0.12D;
    private static final double SPURT_POP_SPEED = 0.09D;
    private static final double SPURT_SPREAD = 0.35D;

    private EldritchMatter() {
    }

    /**
     * The ground breaking open round a point: its own dust thrown up in a ring of {@code radius}
     * with a pale plume between every third pillar, sculk popping in the break and gouts of ink
     * welling up out of the middle. A point with no floor under it throws only the pops and the ink.
     */
    static void erupt(ServerLevel level, Vec3 at, double radius, int pillars, int pops, int ink) {
        RandomSource random = level.getRandom();
        BlockPos floor = floorUnder(level, at);
        if (floor != null && pillars > 0) {
            BlockParticleOption dust = new BlockParticleOption(ParticleTypes.DUST_PILLAR, level.getBlockState(floor));
            double y = Math.min(at.y, floor.getY() + 1.0D) + 0.02D;
            double offset = random.nextDouble() * Math.PI * 2.0D;
            for (int i = 0; i < pillars; i++) {
                double a = offset + i * Math.PI * 2.0D / pillars;
                // count 0 hands the lift over exactly; the pillar adds its own scatter upward
                level.sendParticles(dust, at.x + Math.cos(a) * radius, y, at.z + Math.sin(a) * radius, 0, 0.0D, 1.0D, 0.0D, PILLAR_LIFT);
            }
            int plumes = pillars / PILLARS_PER_PLUME;
            for (int i = 0; i < plumes; i++) {
                // between the pillars, leaning out over the rim
                double a = offset + (i + 0.5D) * Math.PI * 2.0D / plumes;
                double cos = Math.cos(a);
                double sin = Math.sin(a);
                level.sendParticles(ParticleTypes.DUST_PLUME, at.x + cos * radius, y, at.z + sin * radius, 0, cos, 0.0D, sin, PLUME_SPREAD);
            }
        }
        if (pops > 0) {
            level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, at.x, at.y + 0.15D, at.z, pops, radius * 0.4D, 0.1D, radius * 0.4D, POP_SCATTER);
        }
        if (ink <= 0) {
            return;
        }
        if (nearAnEye(level, at, null)) {
            ItemParticleOption fleck = inkFleck();
            for (int i = 0; i < ink * FLECKS_PER_BLOT; i++) {
                double a = random.nextDouble() * Math.PI * 2.0D;
                double lift = FLECK_LIFT * (0.8D + 0.4D * random.nextDouble());
                level.sendParticles(fleck, at.x, at.y + 0.2D, at.z, 0, Math.cos(a) * FLECK_LEAN, lift, Math.sin(a) * FLECK_LEAN, 1.0D);
            }
            return;
        }
        for (int i = 0; i < ink; i++) {
            double a = random.nextDouble() * Math.PI * 2.0D;
            double lift = INK_LIFT * (0.8D + 0.4D * random.nextDouble());
            level.sendParticles(ParticleTypes.GLOW_SQUID_INK, at.x, at.y + 0.2D, at.z, 0, Math.cos(a) * INK_LEAN, lift, Math.sin(a) * INK_LEAN, 1.0D);
        }
    }

    /**
     * A hold tightening: sculk popping out round the middle of the body the grip closes on. No
     * ink, because the deep's grasp on a Noticed mage holds the player whose camera it is - and a
     * held player is not sent the pops either: they scatter round the body's axis, which is where
     * that camera stands, and a pop is a third of a block across.
     */
    static void squeeze(ServerLevel level, LivingEntity held, int pops) {
        Vec3 c = held.getBoundingBox().getCenter();
        double across = held.getBbWidth() * 0.45D;
        send(level, asPlayer(held), ParticleTypes.SCULK_CHARGE_POP, c.x, c.y, c.z, pops, across, held.getBbHeight() * 0.3D, across, POP_SCATTER);
    }

    /**
     * Ink and sculk flung one way off a point: a sting landing on a body, a ward torn off a back.
     * Within {@value #INK_CLEARANCE} blocks of an eye the ink goes as flecks. When the point is on a
     * player's own body ({@code unseenBy}, else null) that player is not sent it: it starts under
     * their camera, and off a body they are looking down at it would open inside their view.
     */
    static void spurt(ServerLevel level, Vec3 from, Vec3 dir, int ink, int pops, ServerPlayer unseenBy) {
        RandomSource random = level.getRandom();
        Vec3 d = dir.lengthSqr() > 1.0E-6D ? dir.normalize() : new Vec3(0.0D, 1.0D, 0.0D);
        boolean close = ink > 0 && nearAnEye(level, from, unseenBy);
        int blots = close ? 0 : ink;
        int flecks = close ? ink * FLECKS_PER_BLOT : 0;
        ItemParticleOption fleck = flecks > 0 ? inkFleck() : null;
        for (int i = 0; i < blots + flecks + pops; i++) {
            Vec3 v = d.add(jitter(random), jitter(random), jitter(random));
            if (i < blots) {
                send(level, unseenBy, ParticleTypes.GLOW_SQUID_INK, from.x, from.y, from.z, 0, v.x, v.y, v.z, SPURT_INK_SPEED);
            } else if (i < blots + flecks) {
                send(level, unseenBy, fleck, from.x, from.y, from.z, 0, v.x, v.y, v.z, SPURT_FLECK_SPEED);
            } else {
                send(level, unseenBy, ParticleTypes.SCULK_CHARGE_POP, from.x, from.y, from.z, 0, v.x, v.y, v.z, SPURT_POP_SPEED);
            }
        }
    }

    /**
     * An eye's stare landing: sculk streaking off the pupil into the body it watches, and a few of
     * the deep's souls lifting off that body. Small and light, and thrown away from whoever cast the
     * eye, since it hangs on their side of what it watches. A stung player is not sent it: the pops
     * fly at their chest and the souls rise up through their own camera.
     */
    static void stare(ServerLevel level, Vec3 pupil, LivingEntity seen, int pops, int souls) {
        RandomSource random = level.getRandom();
        ServerPlayer unseenBy = asPlayer(seen);
        Vec3 to = seen.getBoundingBox().getCenter();
        Vec3 line = to.subtract(pupil);
        double length = line.length();
        if (length > 1.0E-3D) {
            Vec3 d = line.scale(1.0D / length);
            for (int i = 0; i < pops; i++) {
                Vec3 v = d.add(stareJitter(random), stareJitter(random), stareJitter(random));
                double speed = length / POP_TRAVEL * (0.8D + 0.4D * random.nextDouble());
                send(level, unseenBy, ParticleTypes.SCULK_CHARGE_POP, pupil.x, pupil.y, pupil.z, 0, v.x, v.y, v.z, speed);
            }
        }
        double across = seen.getBbWidth() * SOUL_ACROSS;
        double tall = seen.getBbHeight() * 0.25D;
        for (int i = 0; i < souls; i++) {
            double x = to.x + (random.nextDouble() - 0.5D) * 2.0D * across;
            double y = to.y + (random.nextDouble() - 0.5D) * 2.0D * tall;
            double z = to.z + (random.nextDouble() - 0.5D) * 2.0D * across;
            send(level, unseenBy, ParticleTypes.SCULK_SOUL, x, y, z, 0, 0.0D, 1.0D, 0.0D, SOUL_LIFT);
        }
    }

    /**
     * One particle packet to every player in the level, or to every player but {@code unseenBy}:
     * the one whose own body a beat is thrown off, whose camera is inside it.
     */
    static <T extends ParticleOptions> void send(ServerLevel level, ServerPlayer unseenBy, T options,
            double x, double y, double z, int count, double dx, double dy, double dz, double speed) {
        if (unseenBy == null) {
            level.sendParticles(options, x, y, z, count, dx, dy, dz, speed);
            return;
        }
        for (ServerPlayer watcher : level.players()) {
            if (watcher != unseenBy) {
                level.sendParticles(watcher, options, false, false, x, y, z, count, dx, dy, dz, speed);
            }
        }
    }

    /** The body as a player whose camera it carries, or null. */
    static ServerPlayer asPlayer(LivingEntity body) {
        return body instanceof ServerPlayer player ? player : null;
    }

    /** Vanilla's own item-break bits of an ink sac: dark flecks that fly and fall, a tenth to a fifth of a block. */
    private static ItemParticleOption inkFleck() {
        return new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.INK_SAC));
    }

    /**
     * True when the eyes of some player who will be sent a beat are close enough to a point that
     * ink thrown there would fill their view; {@code unseenBy} is not sent it, so is not asked.
     */
    private static boolean nearAnEye(ServerLevel level, Vec3 at, ServerPlayer unseenBy) {
        double reach = INK_CLEARANCE * INK_CLEARANCE;
        for (ServerPlayer player : level.players()) {
            if (player != unseenBy && player.getEyePosition().distanceToSqr(at) < reach) {
                return true;
            }
        }
        return false;
    }

    private static double jitter(RandomSource random) {
        return (random.nextDouble() - 0.5D) * 2.0D * SPURT_SPREAD;
    }

    private static double stareJitter(RandomSource random) {
        return (random.nextDouble() - 0.5D) * 2.0D * STARE_SPREAD;
    }

    /** The first block at or under a point that something could stand on, or null within the search. */
    private static BlockPos floorUnder(ServerLevel level, Vec3 at) {
        BlockPos.MutableBlockPos cursor = BlockPos.containing(at.x, at.y - 0.2D, at.z).mutable();
        for (int i = 0; i < FLOOR_SEARCH; i++) {
            if (!level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty()) {
                return cursor.immutable();
            }
            cursor.move(0, -1, 0);
        }
        return null;
    }
}
