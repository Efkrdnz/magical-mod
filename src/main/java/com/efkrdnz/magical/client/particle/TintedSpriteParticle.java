package com.efkrdnz.magical.client.particle;

import com.efkrdnz.magical.magic.visual.AccentPlan;
import com.efkrdnz.magical.particle.TintedParticleOptions;
import net.minecraft.client.Minecraft;
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

    /**
     * How each sprite moves and is drawn. Gravity is vanilla's field, which {@code Particle.tick}
     * spends as {@code 0.04 * gravity} of a block a tick: a block crumb is 1, and the first table
     * here was written in blocks a tick, which left a shard falling a twentieth as fast as a crumb.
     */
    public enum Motion {
        /** Lifts, slows, fades; full bright. Picks one glyph and keeps it. */
        RUNE(0.90F, -0.1F, false, true, true, 18, 10),
        /** Falls, tumbles, settles on what it lands on; lit by the world, so it reads as a thing. */
        SHARD(0.96F, 1.0F, true, false, false, 22, 14),
        /** Drifts, twinkles through its frames, shrinks out; full bright. */
        MOTE(0.88F, 0.0F, false, true, false, 12, 8),
        /** Rises, spreads and thins through its frames; lit by the world, so it is smoke, not light. */
        WISP(0.93F, -0.15F, false, false, false, 26, 16);

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
        int rgb = tint(options, motion);
        this.setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
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
        if (onTheLens(x, y, z)) {
            // a wisp from a pulse at the feet rises past the eyes; one this near is a card across the view
            remove();
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
                alpha = AccentPlan.WISP_OPACITY * (1.0F - t);
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

    /** Whether a point is within AccentPlan.SPRITE_NEAR_CAMERA of a first-person camera; the sigil asks it too. */
    static boolean onTheLens(double x, double y, double z) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.options.getCameraType().isFirstPerson()
                && minecraft.gameRenderer.getMainCamera().getPosition().distanceToSqr(x, y, z) < AccentPlan.SPRITE_NEAR_CAMERA * AccentPlan.SPRITE_NEAR_CAMERA;
    }

    /**
     * The tint as drawn. Light - a rune, a mote - is never darker than
     * {@link AccentPlan#LIGHT_SPRITE_FLOOR}, a shard than {@link AccentPlan#SHARD_FLOOR} and smoke than
     * {@link AccentPlan#WISP_FLOOR}, whatever colour the spell asked for.
     */
    private static int tint(TintedParticleOptions options, Motion motion) {
        int rgb = (Math.round(options.red() * 255.0F) << 16) | (Math.round(options.green() * 255.0F) << 8) | Math.round(options.blue() * 255.0F);
        return switch (motion) {
            case RUNE, MOTE -> AccentPlan.lift(rgb, AccentPlan.LIGHT_SPRITE_FLOOR);
            case SHARD -> AccentPlan.lift(rgb, AccentPlan.SHARD_FLOOR);
            case WISP -> AccentPlan.lift(rgb, AccentPlan.WISP_FLOOR);
        };
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
            // not on the lens: see AccentPlan.SPRITE_NEAR_CAMERA
            if (onTheLens(x, y, z)) {
                return null;
            }
            return new TintedSpriteParticle(level, x, y, z, xd, yd, zd, options, sprites, motion);
        }
    }
}
