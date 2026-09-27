package com.efkrdnz.magical.client.particle;

import com.efkrdnz.magical.particle.TintedParticleOptions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;

/**
 * The mod's four tinted sprites, one class: they differ only in how they move, how they fade and
 * whether the world lights them.
 *
 * <p>The velocity a spawner hands in is kept exactly. Vanilla's own seven-argument constructor
 * re-rolls it with up to 0.4 a block of noise per axis, which is right for a torch's smoke and
 * wrong for a ring of shards thrown out along a surface - so the four-argument constructor is used
 * and the speed is set after it.
 */
public final class TintedSpriteParticle extends TextureSheetParticle {

    /** How each sprite moves and is drawn. */
    public enum Motion {
        /** Lifts, slows, fades; full bright. Picks one glyph and keeps it. */
        RUNE(0.90F, -0.004F, false, true, true, 18, 10),
        /** Falls, tumbles, settles on what it lands on; lit by the world, so it reads as a thing. */
        SHARD(0.96F, 0.045F, true, false, false, 22, 14),
        /** Drifts, twinkles through its frames, shrinks out; full bright. */
        MOTE(0.88F, 0.0F, false, true, false, 12, 8),
        /** Rises, spreads and thins through its frames; lit by the world, so it is smoke, not light. */
        WISP(0.93F, -0.006F, false, false, false, 26, 16);

        private final float friction;
        private final float gravity;
        private final boolean physics;
        private final boolean fullBright;
        private final boolean pickOne;
        private final int life;
        private final int lifeJitter;

        Motion(float friction, float gravity, boolean physics, boolean fullBright, boolean pickOne, int life, int lifeJitter) {
            this.friction = friction;
            this.gravity = gravity;
            this.physics = physics;
            this.fullBright = fullBright;
            this.pickOne = pickOne;
            this.life = life;
            this.lifeJitter = lifeJitter;
        }
    }

    private final SpriteSet sprites;
    private final Motion motion;
    private final float baseSize;
    private final float spin;

    private TintedSpriteParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
            TintedParticleOptions options, SpriteSet sprites, Motion motion) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.motion = motion;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.friction = motion.friction;
        this.gravity = motion.gravity;
        this.hasPhysics = motion.physics;
        this.lifetime = motion.life + random.nextInt(motion.lifeJitter + 1);
        this.setColor(options.red(), options.green(), options.blue());
        this.baseSize = 0.1F * options.scale() * (0.8F + random.nextFloat() * 0.4F);
        this.quadSize = baseSize;
        this.spin = motion == Motion.SHARD ? (random.nextFloat() - 0.5F) * 0.5F : 0.0F;
        this.roll = random.nextFloat() * Mth.TWO_PI * (motion == Motion.SHARD ? 1.0F : 0.0F);
        this.oRoll = roll;
        if (motion.pickOne) {
            pickSprite(sprites);
        } else if (motion == Motion.SHARD) {
            pickSprite(sprites);
        } else {
            setSpriteFromAge(sprites);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) {
            return;
        }
        if (motion == Motion.MOTE || motion == Motion.WISP) {
            setSpriteFromAge(sprites);
        }
        oRoll = roll;
        if (motion == Motion.SHARD && !onGround) {
            roll += spin;
        }
        float t = age / (float) lifetime;
        switch (motion) {
            case RUNE -> {
                alpha = t < 0.6F ? 1.0F : 1.0F - (t - 0.6F) / 0.4F;
                quadSize = baseSize * (1.0F - 0.3F * t);
            }
            case SHARD -> alpha = t < 0.75F ? 1.0F : 1.0F - (t - 0.75F) / 0.25F;
            case MOTE -> quadSize = baseSize * (t < 0.2F ? 0.6F + 2.0F * t : 1.0F - 0.7F * (t - 0.2F) / 0.8F);
            case WISP -> {
                quadSize = baseSize * (1.0F + 1.2F * t);
                alpha = 0.75F * (1.0F - t);
            }
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return motion.fullBright ? 0xF000F0 : super.getLightColor(partialTick);
    }

    /** One provider per sprite set, all four sharing the class. */
    public static final class Provider implements ParticleProvider<TintedParticleOptions> {
        private final SpriteSet sprites;
        private final Motion motion;

        public Provider(SpriteSet sprites, Motion motion) {
            this.sprites = sprites;
            this.motion = motion;
        }

        @Override
        public Particle createParticle(TintedParticleOptions options, ClientLevel level, double x, double y, double z,
                double xd, double yd, double zd) {
            return new TintedSpriteParticle(level, x, y, z, xd, yd, zd, options, sprites, motion);
        }
    }
}
