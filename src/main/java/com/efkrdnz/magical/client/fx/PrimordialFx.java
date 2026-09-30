package com.efkrdnz.magical.client.fx;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.client.particle.TintedSpriteParticle;
import com.efkrdnz.magical.entity.fx.SpellEffectEntity;
import com.efkrdnz.magical.magic.MagicContent;
import com.efkrdnz.magical.magic.primordial.CycloneFeed;
import com.efkrdnz.magical.magic.primordial.Funnel;
import com.efkrdnz.magical.magic.primordial.MassCodec;
import com.efkrdnz.magical.magic.primordial.StormElement;
import com.efkrdnz.magical.magic.skill.primordial.CalderaSkill;
import com.efkrdnz.magical.magic.skill.primordial.CycloneSkill;
import com.efkrdnz.magical.magic.skill.primordial.SkyfallSkill;
import com.efkrdnz.magical.magic.skill.primordial.TsunamiSkill;
import com.efkrdnz.magical.magic.visual.AccentPlan;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import com.efkrdnz.magical.registry.MagicalParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The matter of the Primordial catastrophes, thrown on each client from the effects' synced state:
 * the funnel of a Cyclone as a spiral of tinted smoke in its element's colour with the ground's own
 * dust at its foot, a falling star's fire trail and the ring of embers closing on where it lands,
 * a caldera's smoke column and its bombs' trails, the foam sheet of a Tsunami riding the wave's own
 * velocity, and the dust shaken off a torn-up plate. Honours the particle setting and keeps off a
 * first-person lens the way {@code SpellAccents} does.
 */
@EventBusSubscriber(modid = MagicalMod.MODID, value = Dist.CLIENT)
public final class PrimordialFx {
    private static final double MAX_DISTANCE_SQR = 96.0D * 96.0D;
    private static final int DUST_TINT = 0x9C8C6A;
    private static final int DUST_DARK = 0x6B5E48;
    private static final int FOAM = 0xE4F3FB;
    /** Hard caps a tick, so several catastrophes at once cannot fill the particle engine. */
    private static final int MAX_WALL = 110;
    private static final int MAX_FACE = 140;
    private static final int SEA = 0x3E8FC4;
    private static final int DEEP_SEA = 0x1C4F86;
    private static int cyclone = Integer.MIN_VALUE;
    private static int skyfall;
    private static int caldera;
    private static int tsunami;
    private static int upheaval;

    private PrimordialFx() {}

    private static void resolve() {
        if (cyclone != Integer.MIN_VALUE) {
            return;
        }
        cyclone = MagicContent.skillIndex(MagicContent.CYCLONE.id());
        skyfall = MagicContent.skillIndex(MagicContent.SKYFALL.id());
        caldera = MagicContent.skillIndex(MagicContent.CALDERA.id());
        tsunami = MagicContent.skillIndex(MagicContent.TSUNAMI.id());
        upheaval = MagicContent.skillIndex(MagicContent.UPHEAVAL.id());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused()) {
            return;
        }
        ParticleStatus status = minecraft.options.particles().get();
        if (status == ParticleStatus.MINIMAL) {
            return;
        }
        resolve();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        Vec3 lens = minecraft.options.getCameraType().isFirstPerson() ? camera : null;
        Emitter e = new Emitter(minecraft.particleEngine, level.random, status == ParticleStatus.DECREASED ? 2 : 1, lens);
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof SpellEffectEntity fx) || entity.distanceToSqr(camera) > MAX_DISTANCE_SQR) {
                continue;
            }
            int index = fx.skillIndex();
            if (index == cyclone) {
                cyclone(e, level, fx);
            } else if (index == skyfall) {
                skyfall(e, fx);
            } else if (index == caldera) {
                caldera(e, fx);
            } else if (index == tsunami) {
                tsunami(e, fx);
            } else if (index == upheaval) {
                upheaval(e, fx);
            }
        }
    }

    // ------------------------------------------------------------------ cyclone

    private static void cyclone(Emitter e, ClientLevel level, SpellEffectEntity fx) {
        CycloneFeed.State state = CycloneSkill.read(fx.syncedData());
        StormElement element = state.element();
        Funnel funnel = CycloneSkill.funnel(fx.radius(), element, state.stacks());
        Vec3 eye = fx.position();
        int tint = element == StormElement.DUST ? DUST_TINT : element.color();
        // the wall: a thin shell on the funnel's own radius, small at the foot and swelling with
        // height, so it reads as a funnel rather than a plume
        // Wisps keep their launch speed (less a little friction) and swell as they fade, so a fast
        // spin throws them off the wall into haze: slow, small and many is what holds a shape.
        int wall = Math.min(MAX_WALL, 36 + (int) (funnel.radius() * 10.0D));
        for (int i = 0; i < wall; i += e.stride) {
            double h = funnel.height() * Math.pow(e.random.nextDouble(), 0.9D);
            double r = funnel.radiusAt(h) * (0.92D + 0.16D * e.random.nextDouble());
            double th = e.random.nextDouble() * Math.PI * 2.0D;
            double speed = 0.1D + 0.1D * e.random.nextDouble();
            double cos = Math.cos(th);
            double sin = Math.sin(th);
            // counter-clockwise from above: Funnel.SPIN is the sign of the change of the angle
            double vx = Funnel.SPIN * -sin * speed - cos * 0.02D;
            double vz = Funnel.SPIN * cos * speed - sin * 0.02D;
            int colour = i % 3 == 0 ? DUST_DARK : (i & 3) == 1 ? DUST_TINT : tint;
            float size = (float) (0.6D + 0.6D * e.random.nextDouble() + 1.4D * h / Math.max(1.0D, funnel.height()));
            e.emit(new TintedParticleOptions(MagicalParticles.WISP.get(), colour, size),
                    eye.x + cos * r, eye.y + h, eye.z + sin * r, vx, 0.04D + 0.06D * e.random.nextDouble(), vz);
        }
        // the skirt: the ground's dust dragged round the foot, wider than the funnel it feeds
        for (int i = 0; i < 6; i += e.stride) {
            double th = e.random.nextDouble() * Math.PI * 2.0D;
            double r = funnel.reach() * (0.45D + 0.55D * e.random.nextDouble());
            e.emit(new TintedParticleOptions(MagicalParticles.WISP.get(), DUST_TINT, (float) (2.0D + e.random.nextDouble())),
                    eye.x + Math.cos(th) * r, eye.y + 0.2D + 0.5D * e.random.nextDouble(), eye.z + Math.sin(th) * r,
                    Funnel.SPIN * -Math.sin(th) * 0.35D - Math.cos(th) * 0.08D, 0.03D, Funnel.SPIN * Math.cos(th) * 0.35D - Math.sin(th) * 0.08D);
        }
        BlockState ground = level.getBlockState(BlockPos.containing(eye).below());
        if (!ground.isAir() && ground.getFluidState().isEmpty()) {
            ParticleOptions crumb = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int i = 0; i < 4; i += e.stride) {
                double th = e.random.nextDouble() * Math.PI * 2.0D;
                double r = funnel.radiusAt(0.0D) * 1.4D;
                e.emit(crumb, eye.x + Math.cos(th) * r, eye.y + 0.1D, eye.z + Math.sin(th) * r,
                        Funnel.SPIN * -Math.sin(th) * 0.3D, 0.25D + 0.2D * e.random.nextDouble(), Funnel.SPIN * Math.cos(th) * 0.3D);
            }
        }
        ParticleOptions matter = cycloneMatter(element, ground);
        int bits = element == StormElement.DUST ? 2 : 5 + 2 * state.stacks();
        for (int i = 0; i < bits; i += e.stride) {
            double h = funnel.height() * e.random.nextDouble();
            double r = funnel.radiusAt(h) * 0.9D;
            double th = e.random.nextDouble() * Math.PI * 2.0D;
            e.emit(matter, eye.x + Math.cos(th) * r, eye.y + h, eye.z + Math.sin(th) * r,
                    Funnel.SPIN * -Math.sin(th) * 0.25D, 0.05D, Funnel.SPIN * Math.cos(th) * 0.25D);
        }
        int sinceFed = fx.tickCount - fx.syncedData().getInt(CycloneSkill.KEY_FED);
        if (sinceFed >= 0 && sinceFed < 6) {
            for (int i = 0; i < 16; i += e.stride) {
                double h = funnel.height() * e.random.nextDouble();
                double th = e.random.nextDouble() * Math.PI * 2.0D;
                double r = funnel.radiusAt(h);
                e.emit(new TintedParticleOptions(MagicalParticles.MOTE.get(), element.color(), 1.4F),
                        eye.x + Math.cos(th) * r, eye.y + h, eye.z + Math.sin(th) * r, Math.cos(th) * 0.2D, 0.1D, Math.sin(th) * 0.2D);
            }
        }
    }

    private static ParticleOptions cycloneMatter(StormElement element, BlockState ground) {
        return switch (element) {
            case DUST -> ParticleTypes.DUST_PLUME;
            case EMBER -> ParticleTypes.FLAME;
            case TIDE -> ParticleTypes.SPLASH;
            case FROST -> ParticleTypes.SNOWFLAKE;
            case RADIANT -> ParticleTypes.END_ROD;
            case MAELSTROM -> ParticleTypes.REVERSE_PORTAL;
            case BLIGHT -> ParticleTypes.SQUID_INK;
            case CRIMSON -> new DustParticleOptions(StormElement.CRIMSON.color(), 1.4F);
            case ARCANE -> ParticleTypes.ENCHANT;
            case STEEL -> ParticleTypes.CRIT;
            case STONE -> new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? Blocks.STONE.defaultBlockState() : ground);
            case DEEP -> ParticleTypes.SCULK_SOUL;
        };
    }

    // ------------------------------------------------------------------ skyfall

    private static void skyfall(Emitter e, SpellEffectEntity fx) {
        CompoundTag d = fx.syncedData();
        if (fx.phase() == SpellEffectEntity.PHASE_DONE || !d.contains(SkyfallSkill.KEY_WARN)) {
            return;
        }
        Vec3 star = fx.position();
        Vec3 dir = fx.direction();
        for (int i = 0; i < 5; i += e.stride) {
            e.emit(ParticleTypes.FLAME, star.x + e.jitter(0.9D), star.y + e.jitter(0.9D), star.z + e.jitter(0.9D),
                    -dir.x * 0.15D + e.jitter(0.03D), -dir.y * 0.15D + e.jitter(0.03D), -dir.z * 0.15D + e.jitter(0.03D));
        }
        for (int i = 0; i < 3; i += e.stride) {
            e.emit(ParticleTypes.LARGE_SMOKE, star.x + e.jitter(0.7D), star.y + e.jitter(0.7D), star.z + e.jitter(0.7D),
                    -dir.x * 0.05D, 0.02D, -dir.z * 0.05D);
        }
        e.emit(ParticleTypes.CAMPFIRE_COSY_SMOKE, star.x, star.y, star.z, e.jitter(0.01D), 0.01D, e.jitter(0.01D));
        if ((fx.tickCount & 1) == 0) {
            e.emit(ParticleTypes.LAVA, star.x, star.y, star.z, 0.0D, 0.0D, 0.0D);
        }
        e.emit(new TintedParticleOptions(MagicalParticles.WISP.get(), 0xFF7A2E, 3.0F), star.x, star.y, star.z, -dir.x * 0.1D, -dir.y * 0.1D, -dir.z * 0.1D);
        Vec3 impact = new Vec3(d.getDouble(SkyfallSkill.KEY_IX), d.getDouble(SkyfallSkill.KEY_IY), d.getDouble(SkyfallSkill.KEY_IZ));
        int warn = Math.max(1, d.getInt(SkyfallSkill.KEY_WARN));
        double frac = Math.min(1.0D, fx.tickCount / (double) warn);
        double r = d.getFloat(SkyfallSkill.KEY_RADIUS) * (1.0D - frac) + 0.6D;
        int n = 18;
        for (int i = 0; i < n; i += e.stride) {
            double th = Math.PI * 2.0D * i / n + fx.tickCount * 0.05D;
            e.emit(ParticleTypes.SMALL_FLAME, impact.x + Math.cos(th) * r, impact.y + 0.1D, impact.z + Math.sin(th) * r, 0.0D, 0.01D, 0.0D);
        }
        if (frac > 0.5D) {
            for (int i = 0; i < 2; i += e.stride) {
                double th = e.random.nextDouble() * Math.PI * 2.0D;
                double rr = r * Math.sqrt(e.random.nextDouble());
                e.emit(ParticleTypes.LAVA, impact.x + Math.cos(th) * rr, impact.y + 0.1D, impact.z + Math.sin(th) * rr, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    // ------------------------------------------------------------------ caldera

    private static void caldera(Emitter e, SpellEffectEntity fx) {
        Vec3 at = fx.position();
        if (fx.effectDrawMode() == CalderaSkill.MODE_BOMB) {
            Vec3 v = new Vec3(fx.getX() - fx.xo, fx.getY() - fx.yo, fx.getZ() - fx.zo);
            e.emit(ParticleTypes.FLAME, at.x, at.y, at.z, -v.x * 0.2D + e.jitter(0.02D), -v.y * 0.2D, -v.z * 0.2D + e.jitter(0.02D));
            e.emit(ParticleTypes.SMOKE, at.x, at.y, at.z, e.jitter(0.02D), 0.02D, e.jitter(0.02D));
            if ((fx.tickCount % 3) == 0) {
                e.emit(ParticleTypes.LAVA, at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
            }
            return;
        }
        if (fx.tickCount < CalderaSkill.RISE) {
            return;
        }
        if (fx.phase() == SpellEffectEntity.PHASE_ACTIVE) {
            if ((fx.tickCount & 1) == 0) {
                e.emit(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x + e.jitter(0.3D), at.y, at.z + e.jitter(0.3D), e.jitter(0.01D), 0.07D, e.jitter(0.01D));
            }
            for (int i = 0; i < 2; i += e.stride) {
                e.emit(ParticleTypes.FLAME, at.x + e.jitter(0.3D), at.y - 0.2D, at.z + e.jitter(0.3D), e.jitter(0.02D), 0.08D, e.jitter(0.02D));
            }
            e.emit(new TintedParticleOptions(MagicalParticles.WISP.get(), 0x3A2A22, 3.5F), at.x, at.y + 0.5D, at.z, e.jitter(0.02D), 0.1D, e.jitter(0.02D));
        } else {
            e.emit(ParticleTypes.LARGE_SMOKE, at.x + e.jitter(0.4D), at.y, at.z + e.jitter(0.4D), 0.0D, 0.05D, 0.0D);
        }
    }

    // ------------------------------------------------------------------ tsunami

    private static void tsunami(Emitter e, SpellEffectEntity fx) {
        CompoundTag d = fx.syncedData();
        if (fx.phase() == SpellEffectEntity.PHASE_DONE || !d.contains(TsunamiSkill.KEY_WIDTH)) {
            return;
        }
        double w = d.getFloat(TsunamiSkill.KEY_WIDTH);
        double h = d.getFloat(TsunamiSkill.KEY_HEIGHT);
        Vec3 dir = fx.direction();
        Vec3 perp = new Vec3(-dir.z, 0.0D, dir.x);
        Vec3 c = fx.position();
        Vec3 v = new Vec3(fx.getX() - fx.xo, 0.0D, fx.getZ() - fx.zo);
        // the face: a solid sheet of sea, deep at the foot and foaming toward a crest that leans
        // out over it, carried at the wave's own speed so it holds together as a wall
        int n = Math.min(MAX_FACE, (int) (w * h * 3.0D) + 12);
        for (int i = 0; i < n; i += e.stride) {
            double u = (e.random.nextDouble() - 0.5D) * w;
            double f = Math.pow(e.random.nextDouble(), 0.7D);
            double y = f * h;
            double lean = 1.1D * f * f;
            Vec3 p = c.add(perp.scale(u)).add(dir.scale(lean - 0.4D * e.random.nextDouble()));
            int colour = f > 0.8D ? FOAM : mix(DEEP_SEA, SEA, f / 0.8D);
            e.emit(new TintedParticleOptions(MagicalParticles.WISP.get(), colour, (float) (2.6D + 1.4D * e.random.nextDouble())),
                    p.x, p.y + y, p.z, v.x, 0.005D, v.z);
        }
        for (int i = 0; i < (int) w; i += e.stride) {
            double u = (e.random.nextDouble() - 0.5D) * w;
            Vec3 p = c.add(perp.scale(u)).add(dir.scale(0.9D));
            e.emit(ParticleTypes.CLOUD, p.x, p.y + h, p.z, v.x * 1.4D, 0.04D, v.z * 1.4D);
            e.emit(ParticleTypes.FALLING_WATER, p.x, p.y + h * 0.9D, p.z, 0.0D, 0.0D, 0.0D);
            e.emit(ParticleTypes.SPLASH, p.x, p.y + 0.2D, p.z, v.x, 0.2D, v.z);
        }
    }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ upheaval

    private static void upheaval(Emitter e, SpellEffectEntity fx) {
        int[] states = fx.syncedData().getIntArray(MassCodec.STATES);
        if (states.length == 0) {
            return;
        }
        BlockState state = Block.stateById(states[e.random.nextInt(states.length)]);
        if (state.isAir()) {
            return;
        }
        Vec3 at = fx.position();
        if (fx.phase() == SpellEffectEntity.PHASE_ACTIVE) {
            e.emit(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x + e.jitter(1.5D), at.y + e.jitter(0.8D), at.z + e.jitter(1.5D), 0.0D, 0.0D, 0.0D);
        } else {
            for (int i = 0; i < 3; i += e.stride) {
                e.emit(new BlockParticleOption(ParticleTypes.FALLING_DUST, state), at.x + e.jitter(2.2D), at.y - 1.6D, at.z + e.jitter(2.2D), 0.0D, 0.0D, 0.0D);
            }
        }
    }

    // ------------------------------------------------------------------ emitter

    private static final class Emitter {
        private final ParticleEngine engine;
        private final RandomSource random;
        private final int stride;
        private final Vec3 lens;

        Emitter(ParticleEngine engine, RandomSource random, int stride, Vec3 lens) {
            this.engine = engine;
            this.random = random;
            this.stride = stride;
            this.lens = lens;
        }

        double jitter(double amount) {
            return (random.nextDouble() - 0.5D) * 2.0D * amount;
        }

        void emit(ParticleOptions options, double x, double y, double z, double vx, double vy, double vz) {
            if (lens != null && lens.distanceToSqr(x, y, z) < AccentPlan.SPRITE_NEAR_CAMERA * AccentPlan.SPRITE_NEAR_CAMERA) {
                return;
            }
            Particle particle = engine.createParticle(options, x, y, z, 0.0D, 0.0D, 0.0D);
            if (particle != null && !(particle instanceof TintedSpriteParticle && vx == 0.0D && vy == 0.0D && vz == 0.0D)) {
                particle.setParticleSpeed(vx, vy, vz);
            }
        }
    }
}
