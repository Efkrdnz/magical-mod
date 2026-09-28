package com.efkrdnz.magical.client.fx;

import com.efkrdnz.magical.client.particle.TintedSpriteParticle;
import com.efkrdnz.magical.entity.fx.ProfiledEffect;
import com.efkrdnz.magical.magic.visual.Accent;
import com.efkrdnz.magical.magic.visual.AccentPlan;
import com.efkrdnz.magical.magic.visual.ColorRole;
import com.efkrdnz.magical.magic.visual.Palette;
import com.efkrdnz.magical.magic.visual.VisualProfile;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import java.util.ArrayDeque;
import java.util.Iterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The matter layer: a cue's real particles, by the profile's {@link Accent}.
 *
 * <p>The shader pass in {@link TransientVisuals} draws what a spell is made of when it is light -
 * the circle, the flash, the mark. This draws what it is made of when it is stuff: flame and smoke,
 * spray and drips, snow and shards, crumbs of whatever it hit. All of it goes through
 * {@link ParticleEngine#createParticle}, which hands the particle back so its speed can be set
 * exactly (every vanilla provider re-rolls the one it is given), and which skips
 * {@code LevelRenderer}'s particle-setting gate - so the gate is asked here, the way
 * {@link SwordImpactParticles} asks it: nothing at Minimal, every other particle at Decreased,
 * nothing past 32 blocks. Every count comes from {@link AccentPlan}.
 */
public final class SpellAccents {

    /** Vanilla's own particle cut-off, from {@code LevelRenderer.addParticleInternal}: 32 blocks. */
    private static final double MAX_DISTANCE_SQR = 1024.0D;

    /**
     * An electric spark's speed. The speed is set on the particle directly, past the 0.25 factor
     * vanilla's own provider applies, and a spark keeps 0.96 of it a tick - so at the speed a flame
     * is thrown at it glides across the whole stage and litters it with glints far from the hit.
     */
    private static final double SPARK_SPEED = 0.09D;

    /** The most splashes one hit throws: a tier-four surge at scale two asked for sixty-eight. */
    private static final int SPLASH_CAP = 36;

    /** The last few blasts, where and when, so a hit near one of them rings as an echo. */
    private static final int BLAST_MEMORY = 16;
    private static final double[] BLAST_X = new double[BLAST_MEMORY];
    private static final double[] BLAST_Y = new double[BLAST_MEMORY];
    private static final double[] BLAST_Z = new double[BLAST_MEMORY];
    private static final long[] BLAST_TICK = new long[BLAST_MEMORY];
    private static int blastNext;

    static {
        java.util.Arrays.fill(BLAST_TICK, Long.MIN_VALUE / 2);
    }

    private SpellAccents() {
    }

    // ------------------------------------------------------------------ cues

    /** Where a hit lands: the element's matter, crumbs of the struck block, and a blast if it is heavy. */
    public static void impact(VisualProfile profile, Vec3 pos, Vec3 normal, float scale, boolean onBody) {
        Emitter e = Emitter.at(pos);
        if (e == null || !profile.accent().active()) {
            return;
        }
        Vec3 n = unit(normal);
        Vec3 p = pos.add(n.scale(0.1D));
        int tier = profile.tier().tier();
        int k = AccentPlan.impactCount(tier, scale);
        int bright = profile.color(ColorRole.BRIGHT);
        int base = profile.color(ColorRole.BASE);
        int dim = profile.color(ColorRole.DIM);
        switch (profile.accent()) {
            case EMBER -> {
                e.spray(ParticleTypes.FLAME, k, p, n, 0.16D, 0.8D);
                e.rise(ParticleTypes.LARGE_SMOKE, k / 3, p, 0.4D, 0.05D);
                if (tier >= 2) {
                    e.spray(ParticleTypes.LAVA, 2, p, n, 0.2D, 0.6D);
                }
            }
            case SPLASH -> {
                e.spray(ParticleTypes.SPLASH, Math.min(k * 2, SPLASH_CAP), p, n, 0.22D, 0.9D);
                e.spray(ParticleTypes.BUBBLE_POP, k / 2, p, n, 0.08D, 1.0D);
                e.spray(ParticleTypes.FALLING_WATER, k / 3, p, n, 0.18D, 0.7D);
                e.rise(wisp(soften(bright), 2.2F), Math.max(1, k / 5), p, 0.5D, 0.02D);
            }
            case FROST -> {
                e.spray(ParticleTypes.SNOWFLAKE, k, p, n, 0.12D, 1.0D);
                e.spray(shard(frost(bright), 1.4F), k / 2, p, n, 0.22D, 0.8D);
                e.spray(ParticleTypes.ITEM_SNOWBALL, k / 3, p, n, 0.16D, 0.8D);
                e.rise(ParticleTypes.WHITE_SMOKE, 2, p, 0.5D, 0.02D);
            }
            case RADIANT -> {
                // an end rod lives three seconds: on a body struck every half second they pile up
                e.spray(ParticleTypes.END_ROD, onBody ? Math.max(1, k / 4) : Math.max(2, k / 2), p, n, 0.1D, 1.0D);
                e.spray(mote(bright, 1.6F), k, p, n, 0.14D, 1.0D);
            }
            case UMBRA -> {
                // a reverse-portal mote hardly moves for the first half of its life, so a full count
                // sits on the target as a still magenta clump for seconds: few of them, and motes
                e.spray(ParticleTypes.REVERSE_PORTAL, Math.max(2, k / 3), p, n, 0.1D, 1.0D);
                e.spray(mote(bright, 1.4F), k / 2, p, n, 0.16D, 1.0D);
                e.rise(wisp(dim, 2.0F), Math.max(1, k / 3), p, 0.5D, 0.03D);
            }
            case RIFT -> {
                e.spray(mote(bright, 1.5F), k, p, n, 0.16D, 1.0D);
                e.spray(ParticleTypes.ELECTRIC_SPARK, k / 2, p, n, SPARK_SPEED, 1.0D);
                e.spray(shard(base, 1.2F), k / 3, p, n, 0.2D, 0.8D);
            }
            case SOUL -> {
                e.spray(ParticleTypes.SOUL_FIRE_FLAME, k / 2, p, n, 0.08D, 1.0D);
                e.rise(ParticleTypes.SOUL, Math.max(1, k / 4), p, 0.4D, 0.05D);
                e.spray(mote(bright, 1.4F), k / 2, p, n, 0.1D, 1.0D);
            }
            case RUNE -> {
                e.spray(rune(bright, 1.4F), Math.max(2, k / 2), p, n, 0.12D, 1.0D);
                e.spray(mote(base, 1.3F), k / 2, p, n, 0.14D, 1.0D);
                e.spray(shard(bright, 1.1F), k / 4, p, n, 0.18D, 0.8D);
            }
            case GLOOM -> {
                e.rise(wisp(dim, 2.0F), Math.max(1, k / 2), p, 0.5D, 0.03D);
                e.spray(ParticleTypes.SMOKE, k, p, n, 0.08D, 1.0D);
                e.spray(ParticleTypes.SQUID_INK, Math.max(1, k / 4), p, n, 0.06D, 1.0D);
            }
            case GORE -> {
                e.spray(new DustParticleOptions(base, 1.3F), k, p, n, 0.15D, 0.9D);
                e.rise(wisp(dim, 2.0F), Math.max(1, k / 4), p, 0.4D, 0.02D);
            }
            case EARTH -> {
                // dust and grit, not POOF: a poof is a white cloud as big as a block face, and a
                // tier-four earth hit threw a stack of them over the middle of the frame
                e.spray(ParticleTypes.DUST_PLUME, k, p, n, 0.1D, 1.0D);
                e.spray(ground(e, pos), Math.max(2, k / 2), p, n, 0.22D, 0.8D);
            }
            case DEEP -> {
                // the deep's ink is its own teal: a squid's is a black square half a block across,
                // and thrown off the side of a body the viewer sees, a pile of them stood in front
                // of every target the school touched
                e.spray(ParticleTypes.GLOW_SQUID_INK, Math.max(1, k / 6), p, n, 0.08D, 1.0D);
                e.spray(ParticleTypes.SCULK_CHARGE_POP, k / 2, p, n, 0.05D, 1.0D);
                e.rise(ParticleTypes.SCULK_SOUL, Math.max(1, k / 5), p, 0.4D, 0.03D);
            }
            case CHAOS -> {
                e.spray(mote(bright, 1.5F), k, p, n, 0.18D, 1.0D);
                e.spray(ParticleTypes.WITCH, k / 2, p, n, 0.1D, 1.0D);
            }
            case FORGE -> {
                e.spray(ParticleTypes.CRIT, k, p, n, 0.45D, 0.9D);
                e.spray(ParticleTypes.LAVA, Math.max(1, k / 6), p, n, 0.2D, 0.6D);
                e.rise(ParticleTypes.WHITE_SMOKE, Math.max(1, k / 3), p, 0.3D, 0.05D);
            }
            case BREW -> {
                e.spray(potion(base), k, p, n, 0.12D, 1.0D);
                e.spray(ParticleTypes.BUBBLE_POP, k / 2, p, n, 0.08D, 1.0D);
                e.spray(VIAL, Math.max(1, k / 4), p, n, 0.18D, 0.8D);
            }
            case BLOOM -> {
                // a cherry leaf lives fifteen seconds and drifts: a few, or they litter the sky
                e.spray(ParticleTypes.CHERRY_LEAVES, Math.max(1, k / 3), p, n, 0.08D, 1.0D);
                e.spray(ParticleTypes.HAPPY_VILLAGER, k / 2, p, n, 0.12D, 1.0D);
                e.spray(mote(bright, 1.3F), k / 3, p, n, 0.1D, 1.0D);
            }
            case NONE -> {
                return;
            }
        }
        if (!onBody) {
            BlockState struck = e.level.getBlockState(BlockPos.containing(pos.subtract(n.scale(0.5D))));
            if (!struck.isAir()) {
                int crumbs = AccentPlan.crumbCount(tier, scale) * (profile.accent() == Accent.EARTH ? 2 : 1);
                e.spray(new BlockParticleOption(ParticleTypes.BLOCK, struck), crumbs, p, n, 0.25D, 0.7D);
            }
        }
        if (AccentPlan.heavy(tier, scale) && admitBlast(e, p)) {
            // a surge of water or a shatter of ice is heavy without being a blast
            switch (profile.accent()) {
                case SPLASH -> e.ring(ParticleTypes.SPLASH, AccentPlan.puffCount(tier, scale) * 2, p, n, 0.25D * AccentPlan.clampScale(scale));
                case FROST -> e.ring(ParticleTypes.SNOWFLAKE, AccentPlan.puffCount(tier, scale), p, n, 0.16D * AccentPlan.clampScale(scale));
                case RADIANT, BLOOM, BREW -> e.ring(ParticleTypes.POOF, AccentPlan.puffCount(tier, scale) / 2, p, n, 0.14D * AccentPlan.clampScale(scale));
                // a fold or a tear is not a blast: the space round it breaks like glass
                case RIFT -> {
                    e.ring(shard(bright, 1.3F), AccentPlan.puffCount(tier, scale) * 2, p, n, 0.18D * AccentPlan.clampScale(scale));
                    e.ring(mote(bright, 1.3F), AccentPlan.puffCount(tier, scale), p, n, 0.12D * AccentPlan.clampScale(scale));
                }
                // the ground thrown up round the hit, in the ground's own colour
                case EARTH -> {
                    e.ring(ground(e, pos), AccentPlan.puffCount(tier, scale) * 2, p, n, 0.2D * AccentPlan.clampScale(scale));
                    e.ring(ParticleTypes.DUST_PLUME, AccentPlan.puffCount(tier, scale), p, n, 0.12D * AccentPlan.clampScale(scale));
                }
                default -> {
                    e.blast(p, scale);
                    e.ring(ParticleTypes.POOF, AccentPlan.puffCount(tier, scale), p, n, 0.18D * AccentPlan.clampScale(scale));
                }
            }
        }
    }

    /** As the spell leaves the hand: a mouthful of its matter along the aim. */
    public static void release(VisualProfile profile, Vec3 pos, Vec3 dir) {
        release(profile, pos, dir, false);
    }

    /**
     * The release as its own caster sees it, when {@code own}: thrown from
     * {@link AccentPlan#OWN_RELEASE_PUSH} further along the aim and at half the count. From the hand
     * itself it landed on the caster's crosshair; not thrown at all, no cast read as leaving the hand.
     */
    public static void release(VisualProfile profile, Vec3 hand, Vec3 dir, boolean own) {
        Vec3 d = unit(dir);
        Vec3 pos = own ? hand.add(d.scale(AccentPlan.OWN_RELEASE_PUSH)) : hand;
        Emitter e = Emitter.at(pos);
        if (e == null) {
            return;
        }
        int count = AccentPlan.releaseCount(profile.tier().tier());
        if (own) {
            count = Math.max(1, count / 2);
        }
        int bright = profile.color(ColorRole.BRIGHT);
        switch (profile.accent()) {
            case EMBER -> {
                e.spray(ParticleTypes.SMALL_FLAME, count, pos, d, 0.12D, 0.35D);
                e.spray(ParticleTypes.SMOKE, 2, pos, d, 0.05D, 0.5D);
            }
            case SPLASH -> e.spray(ParticleTypes.SPLASH, count * 2, pos, d, 0.2D, 0.4D);
            case FROST -> e.spray(ParticleTypes.SNOWFLAKE, count, pos, d, 0.1D, 0.4D);
            case RADIANT -> e.spray(mote(bright, 1.2F), count, pos, d, 0.12D, 0.4D);
            case UMBRA -> e.spray(ParticleTypes.REVERSE_PORTAL, count, pos, d, 0.08D, 0.5D);
            case RIFT -> {
                e.spray(mote(bright, 1.2F), count, pos, d, 0.12D, 0.4D);
                e.spray(ParticleTypes.ELECTRIC_SPARK, 2, pos, d, SPARK_SPEED, 0.4D);
            }
            case SOUL -> e.spray(ParticleTypes.SOUL_FIRE_FLAME, count, pos, d, 0.06D, 0.4D);
            case RUNE -> e.spray(rune(bright, 1.1F), count, pos, d, 0.1D, 0.45D);
            case GLOOM -> e.spray(ParticleTypes.SMOKE, count, pos, d, 0.06D, 0.5D);
            case GORE -> e.spray(new DustParticleOptions(profile.color(ColorRole.BASE), 1.0F), count, pos, d, 0.12D, 0.4D);
            case EARTH -> e.spray(ParticleTypes.DUST_PLUME, count, pos, d, 0.06D, 0.5D);
            case DEEP -> e.spray(ParticleTypes.GLOW_SQUID_INK, 2, pos, d, 0.05D, 0.5D);
            case CHAOS -> e.spray(ParticleTypes.WITCH, count, pos, d, 0.1D, 0.4D);
            case FORGE -> {
                e.spray(ParticleTypes.CRIT, count, pos, d, 0.3D, 0.4D);
                e.spray(ParticleTypes.WHITE_SMOKE, 2, pos, d, 0.05D, 0.5D);
            }
            case BREW -> {
                e.spray(potion(profile.color(ColorRole.BASE)), count, pos, d, 0.1D, 0.4D);
                e.spray(ParticleTypes.BUBBLE_POP, 2, pos, d, 0.06D, 0.5D);
            }
            case BLOOM -> e.spray(ParticleTypes.HAPPY_VILLAGER, count, pos, d, 0.1D, 0.4D);
            case NONE -> { }
        }
    }

    /**
     * Runes lifting off a windup's band toward the middle of the circle and up out of it: the
     * textured successor of the shader motes the windup used to throw.
     */
    public static void windup(VisualProfile profile, Vec3 centre, float radius, int windupTicks) {
        Emitter e = Emitter.at(centre);
        if (e == null) {
            return;
        }
        int runes = AccentPlan.windupRunes(profile.tier().tier());
        ParticleOptions glyph = rune(profile.color(ColorRole.BRIGHT), 1.0F);
        float offset = e.random.nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < runes; i += e.stride) {
            float a = offset + i / (float) runes * Mth.TWO_PI;
            Vec3 from = centre.add(Math.cos(a) * radius, 0.08D, Math.sin(a) * radius);
            Vec3 to = centre.add(0.0D, 1.1D, 0.0D);
            // the rune sprite keeps nine tenths of its speed a tick, so it travels ten times what it starts with
            Vec3 v = to.subtract(from).scale(0.1D * Math.min(1.0D, 12.0D / Math.max(4, windupTicks) + 0.4D));
            e.emit(glyph, from.x, from.y, from.z, v.x, v.y, v.z);
        }
    }

    /** A zone's pulse: its matter rising out of the ground inside it. */
    public static void zone(VisualProfile profile, Vec3 centre, float radius) {
        Emitter e = Emitter.at(centre);
        if (e == null) {
            return;
        }
        int bright = profile.color(ColorRole.BRIGHT);
        int dim = profile.color(ColorRole.DIM);
        ParticleOptions rising = switch (profile.accent()) {
            case EMBER -> ParticleTypes.FLAME;
            case SPLASH -> ParticleTypes.SPLASH;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case RADIANT -> mote(bright, 1.3F);
            // not reverse-portal motes: they hardly move for their first thirty ticks and stood on
            // the ground as still magenta clumps after every pulse
            case UMBRA -> mote(bright, 1.2F);
            case RIFT -> mote(bright, 1.2F);
            case SOUL -> ParticleTypes.SOUL;
            case RUNE -> rune(bright, 1.1F);
            case GLOOM -> wisp(dim, 2.2F);
            case GORE -> new DustParticleOptions(profile.color(ColorRole.BASE), 1.1F);
            case EARTH -> ParticleTypes.DUST_PLUME;
            case DEEP -> ParticleTypes.SCULK_SOUL;
            case CHAOS -> ParticleTypes.WITCH;
            case FORGE -> ParticleTypes.WHITE_SMOKE;
            case BREW -> potion(profile.color(ColorRole.BASE));
            case BLOOM -> ParticleTypes.HAPPY_VILLAGER;
            case NONE -> null;
        };
        if (rising != null) {
            e.rise(rising, AccentPlan.zoneCount(radius), centre.add(0.0D, 0.1D, 0.0D), radius, 0.04D);
        }
    }

    /** A barrier struck: a few fragments of it thrown back off the face. */
    public static void barrierHit(VisualProfile profile, Vec3 pos, Vec3 normal) {
        Emitter e = Emitter.at(pos);
        if (e == null) {
            return;
        }
        Vec3 n = unit(normal);
        int bright = profile.color(ColorRole.BRIGHT);
        e.spray(shard(bright, 1.2F), AccentPlan.BARRIER_SHARDS, pos, n, 0.18D, 0.7D);
        e.spray(mote(bright, 1.0F), 2, pos, n, 0.1D, 1.0D);
    }

    // ------------------------------------------------------------------ trails

    /**
     * Everything profile-driven that is flying: a thread of its matter behind it. Asked of every
     * rendered entity once a client tick, so a spell trails without its entity carrying a line of
     * client code or the server sending a packet for it.
     */
    public static void tickTrails(ClientLevel level) {
        sweepLens(level);
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof ProfiledEffect effect) {
                trail(entity, effect);
            }
        }
    }

    private static void trail(Entity entity, ProfiledEffect effect) {
        VisualProfile profile = effect.profile();
        if (!profile.accent().active()) {
            return;
        }
        double dx = entity.getX() - entity.xo;
        double dy = entity.getY() - entity.yo;
        double dz = entity.getZ() - entity.zo;
        double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int count = AccentPlan.trailCount(speed);
        if (count <= 0) {
            return;
        }
        Vec3 now = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        Emitter e = Emitter.at(now);
        if (e == null) {
            return;
        }
        Vec3 back = new Vec3(-dx, -dy, -dz).scale(0.04D / Math.max(1.0E-4D, speed));
        int bright = profile.color(ColorRole.BRIGHT);
        boolean odd = (entity.tickCount & 1) == 1;
        for (int i = 0; i < count; i += e.stride) {
            double f = (i + e.random.nextDouble()) / count;
            ParticleOptions options = switch (profile.accent()) {
                case EMBER -> odd ? ParticleTypes.SMOKE : ParticleTypes.FLAME;
                case SPLASH -> odd ? ParticleTypes.FALLING_WATER : ParticleTypes.SPLASH;
                case FROST -> ParticleTypes.SNOWFLAKE;
                case RADIANT -> odd ? ParticleTypes.END_ROD : mote(bright, 1.1F);
                case UMBRA -> odd ? ParticleTypes.REVERSE_PORTAL : mote(bright, 1.0F);
                case RIFT -> mote(bright, 1.0F);
                case SOUL -> ParticleTypes.SOUL_FIRE_FLAME;
                case RUNE -> odd ? rune(bright, 0.9F) : mote(bright, 0.9F);
                case GLOOM -> ParticleTypes.SMOKE;
                case GORE -> new DustParticleOptions(profile.color(ColorRole.BASE), 0.9F);
                case EARTH -> ParticleTypes.DUST_PLUME;
                // nothing in the Eldritch school flies: what moves is a construct carried on its
                // wielder (a skin, an eye over the head), which trailed ink wherever they walked
                case DEEP -> null;
                case CHAOS -> ParticleTypes.WITCH;
                // heat off metal, not black smoke: a falling anvil left a column of black cards
                case FORGE -> odd ? ParticleTypes.WHITE_SMOKE : ParticleTypes.CRIT;
                case BREW -> potion(profile.color(ColorRole.BASE));
                case BLOOM -> mote(bright, 0.9F);
                case NONE -> null;
            };
            if (options != null) {
                e.emit(options, now.x - dx * f, now.y - dy * f, now.z - dz * f,
                        back.x + e.jitter(0.015D), back.y + e.jitter(0.015D), back.z + e.jitter(0.015D));
            }
        }
    }

    // ------------------------------------------------------------------ the mod's sprites

    private static ParticleOptions rune(int rgb, float scale) {
        return new TintedParticleOptions(MagicalParticles.RUNE.get(), rgb, scale);
    }

    private static ParticleOptions shard(int rgb, float scale) {
        return new TintedParticleOptions(MagicalParticles.SHARD.get(), rgb, scale);
    }

    private static ParticleOptions mote(int rgb, float scale) {
        return new TintedParticleOptions(MagicalParticles.MOTE.get(), rgb, scale);
    }

    private static ParticleOptions wisp(int rgb, float scale) {
        return new TintedParticleOptions(MagicalParticles.WISP.get(), rgb, scale);
    }

    /** The shards a thrown splash potion breaks into: vanilla's own splash, item and all. */
    private static final ParticleOptions VIAL = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.SPLASH_POTION));

    /** Vanilla's potion swirl, in the spell's colour rather than an effect's. */
    private static ParticleOptions potion(int rgb) {
        return ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | (rgb & 0xFFFFFF));
    }

    /**
     * Whether a heavy hit here may blow up: not within reach of a first-person camera, where the
     * sprite is the whole view, and not on top of a blast of the last second (AccentPlan.blastEchoes).
     * A hit that may is remembered, so the next one near it is its echo.
     */
    private static boolean admitBlast(Emitter e, Vec3 p) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.getCameraType().isFirstPerson()
                && minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(p) < AccentPlan.OWN_BLAST_REACH * AccentPlan.OWN_BLAST_REACH) {
            return false;
        }
        long now = e.level.getGameTime();
        for (int i = 0; i < BLAST_MEMORY; i++) {
            double dx = BLAST_X[i] - p.x;
            double dy = BLAST_Y[i] - p.y;
            double dz = BLAST_Z[i] - p.z;
            if (AccentPlan.blastEchoes(now - BLAST_TICK[i], dx * dx + dy * dy + dz * dz)) {
                return false;
            }
        }
        BLAST_X[blastNext] = p.x;
        BLAST_Y[blastNext] = p.y;
        BLAST_Z[blastNext] = p.z;
        BLAST_TICK[blastNext] = now;
        blastNext = (blastNext + 1) % BLAST_MEMORY;
        return true;
    }

    /**
     * Crumbs of the ground under a point: the first block that is not air in the two below it,
     * or dirt when the hit was in the open air.
     */
    private static ParticleOptions ground(Emitter e, Vec3 pos) {
        BlockPos at = BlockPos.containing(pos);
        for (int i = 0; i < 3; i++) {
            BlockState state = e.level.getBlockState(at.below(i));
            if (!state.isAir()) {
                return new BlockParticleOption(ParticleTypes.BLOCK, state);
            }
        }
        return new BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
    }

    /** Halfway to white: water's mist is paler than water. */
    private static int soften(int rgb) {
        return Palette.mix(rgb, 0xFFFFFF, 0.5F);
    }

    /** Most of the way to white with the palette's cold kept: ice is nearly colourless. */
    private static int frost(int rgb) {
        return Palette.mix(rgb, 0xF4FAFF, 0.65F);
    }

    private static Vec3 unit(Vec3 v) {
        double length = v == null ? 0.0D : v.length();
        return length < 1.0E-6D ? new Vec3(0.0D, 1.0D, 0.0D) : v.scale(1.0D / length);
    }

    /**
     * How near a first-person camera a vanilla particle this layer threw may be born and still be
     * watched as it flies. None is born within AccentPlan.SPRITE_NEAR_CAMERA of the lens, but a
     * crumb thrown up at the feet or a flake spun off a body beside the caster rises or drifts
     * through the eyes a few ticks later. The mod's own sprites check themselves every tick; a
     * vanilla particle cannot, so the ones born this near are kept here and swept.
     */
    private static final double LENS_WATCH = 4.0D;
    /** At most this many are watched; past it the oldest is let go unwatched. */
    private static final int LENS_WATCH_CAP = 256;
    private static final ArrayDeque<Particle> WATCHED = new ArrayDeque<>();
    private static ClientLevel watchedLevel;

    /** Drops every watched particle that has died and removes every one now on the lens. */
    private static void sweepLens(ClientLevel level) {
        if (level != watchedLevel) {
            // a new level clears the particle engine without removing anything, so nothing here is alive
            WATCHED.clear();
            watchedLevel = level;
        }
        if (WATCHED.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        Vec3 lens = minecraft.gameRenderer.getMainCamera().getPosition();
        double near = AccentPlan.SPRITE_NEAR_CAMERA * AccentPlan.SPRITE_NEAR_CAMERA;
        for (Iterator<Particle> it = WATCHED.iterator(); it.hasNext();) {
            Particle particle = it.next();
            if (!particle.isAlive()) {
                it.remove();
            } else if (firstPerson && lens.distanceToSqr(particle.getBoundingBox().getCenter()) < near) {
                particle.remove();
                it.remove();
            }
        }
    }

    private static void watch(Particle particle) {
        if (WATCHED.size() >= LENS_WATCH_CAP) {
            WATCHED.pollFirst();
        }
        WATCHED.addLast(particle);
    }

    /** One burst's worth of spawning, gated by the particle setting and the distance once. */
    private static final class Emitter {
        private final ClientLevel level;
        private final ParticleEngine engine;
        private final RandomSource random;
        private final int stride;
        /** A first-person camera, which no particle is born within AccentPlan.SPRITE_NEAR_CAMERA of; null otherwise. */
        private final Vec3 lens;

        private Emitter(ClientLevel level, ParticleEngine engine, int stride, Vec3 lens) {
            this.level = level;
            this.engine = engine;
            this.random = level.random;
            this.stride = stride;
            this.lens = lens;
        }

        static Emitter at(Vec3 pos) {
            Minecraft minecraft = Minecraft.getInstance();
            ClientLevel level = minecraft.level;
            if (level == null) {
                return null;
            }
            ParticleStatus status = minecraft.options.particles().get();
            if (status == ParticleStatus.MINIMAL) {
                return null;
            }
            Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
            if (camera.distanceToSqr(pos) > MAX_DISTANCE_SQR) {
                return null;
            }
            Vec3 lens = minecraft.options.getCameraType().isFirstPerson() ? camera : null;
            return new Emitter(level, minecraft.particleEngine, status == ParticleStatus.DECREASED ? 2 : 1, lens);
        }

        double jitter(double amount) {
            return (random.nextDouble() - 0.5D) * 2.0D * amount;
        }

        void emit(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz) {
            if (lens != null && lens.distanceToSqr(x, y, z) < AccentPlan.SPRITE_NEAR_CAMERA * AccentPlan.SPRITE_NEAR_CAMERA) {
                return;
            }
            Particle particle = engine.createParticle(options, x, y, z, 0.0D, 0.0D, 0.0D);
            if (particle != null) {
                particle.setParticleSpeed(vx, vy, vz);
                if (lens != null && !(particle instanceof TintedSpriteParticle) && lens.distanceToSqr(x, y, z) < LENS_WATCH * LENS_WATCH) {
                    watch(particle);
                }
            }
        }

        /** Out of a point inside a cone round {@code n}: a cone of 0 is a line, 1 a hemisphere. */
        void spray(ParticleOptions options, int count, Vec3 p, Vec3 n, double speed, double cone) {
            for (int i = 0; i < count; i += stride) {
                Vec3 d = n.add(jitter(cone), jitter(cone), jitter(cone));
                double length = d.length();
                d = length < 1.0E-4D ? n : d.scale(1.0D / length);
                double s = speed * (0.5D + 0.7D * random.nextDouble());
                emit(options, p.x, p.y, p.z, d.x * s, d.y * s, d.z * s);
            }
        }

        /** Up out of a disc of {@code radius} round a point. */
        void rise(ParticleOptions options, int count, Vec3 p, double radius, double lift) {
            for (int i = 0; i < count; i += stride) {
                double r = radius * Math.sqrt(random.nextDouble());
                double a = random.nextDouble() * Math.PI * 2.0D;
                emit(options, p.x + Math.cos(a) * r, p.y, p.z + Math.sin(a) * r,
                        jitter(0.01D), lift * (0.6D + 0.8D * random.nextDouble()), jitter(0.01D));
            }
        }

        /** Puffs run out along the plane the normal is square to. */
        void ring(ParticleOptions options, int count, Vec3 p, Vec3 n, double speed) {
            Vec3 u = Math.abs(n.y) < 0.9D
                    ? n.cross(new Vec3(0.0D, 1.0D, 0.0D)).normalize()
                    : n.cross(new Vec3(1.0D, 0.0D, 0.0D)).normalize();
            Vec3 w = n.cross(u);
            double offset = random.nextDouble() * Math.PI * 2.0D;
            for (int i = 0; i < count; i += stride) {
                double a = offset + i * Math.PI * 2.0D / count;
                Vec3 d = u.scale(Math.cos(a)).add(w.scale(Math.sin(a)));
                emit(options, p.x + d.x * 0.3D, p.y + d.y * 0.3D, p.z + d.z * 0.3D, d.x * speed, d.y * speed, d.z * speed);
            }
        }

        /**
         * Vanilla's explosion sprite. It reads the x speed it is handed as a size, {@code 2 - x}:
         * one at scale 1 is the puff vanilla draws for a creeper, larger as the hit is.
         */
        void blast(Vec3 p, float scale) {
            double size = Mth.clamp(1.0D - (AccentPlan.clampScale(scale) - 1.0D) * 0.5D, 0.3D, 1.2D);
            engine.createParticle(ParticleTypes.EXPLOSION, p.x, p.y, p.z, size, 0.0D, 0.0D);
        }
    }
}
