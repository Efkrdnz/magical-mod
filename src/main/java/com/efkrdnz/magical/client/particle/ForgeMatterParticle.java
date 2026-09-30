package com.efkrdnz.magical.client.particle;

import com.efkrdnz.magical.forge.visual.MatterKind;
import com.efkrdnz.magical.magic.visual.AccentPlan;
import com.efkrdnz.magical.particle.ForgeMatterOptions;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * One piece of what a forged blade throws. Everything about how it moves is its {@link MatterKind};
 * this class is only the part that needs a world: collision, light and a quad.
 *
 * <p>Two things vanilla's particle does not do. It <b>bounces</b>: vanilla's {@code move} stops a
 * particle dead the first time it lands and never moves it again, which is right for a crumb of
 * dirt and wrong for a spark off an anvil, so collision is done here and a kind with a bounce keeps
 * that fraction of its speed off a floor or a wall. And a spark or a gust is drawn <b>stretched
 * along its flight</b>, a streak rather than a dot, because a small bright thing moving fast is a
 * line to the eye and a dot reads as dust hanging still.
 *
 * <p>The velocity a spawner hands in is kept exactly, as {@link TintedSpriteParticle} keeps it.
 */
public final class ForgeMatterParticle extends TextureSheetParticle {

    /** Vanilla's own limit on a move it will run collision for. */
    private static final double MAX_COLLIDE_SQR = 10000.0D;
    /** Slower than this off a floor and it lands rather than bouncing. */
    private static final double MIN_BOUNCE = 0.06D;
    private static final int MAX_BOUNCES = 2;
    /** How much longer than wide a streak is per block a tick of speed, and at most. */
    private static final float STRETCH_PER_SPEED = 6.0F;
    private static final float STRETCH_MAX = 4.0F;
    /** How hard a swaying kind is pushed sideways, and how fast it rocks. */
    private static final float SWAY = 0.006F;
    private static final float SWAY_RATE = 0.35F;
    /** A drop lies splatted this long before it goes, spreading after the first few ticks. */
    private static final int SPLAT_TICKS = 20;
    private static final int SPLAT_SPREAD_AT = 6;
    /** A drop falling faster than this is drawn as a teardrop. */
    private static final double TEARDROP_SPEED = 0.25D;

    private final SpriteSet sprites;
    private final MatterKind kind;
    private final int hot;
    private final int body;
    private final int cool;
    private final float baseSize;
    private final float spin;
    private final float phase;
    private int bounces;
    private boolean settled;
    private int settledAt;

    private ForgeMatterParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
            ForgeMatterOptions options, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.kind = options.kind();
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.friction = kind.friction();
        this.gravity = kind.gravity();
        this.hasPhysics = kind.gravity() > 0.0F;
        this.lifetime = kind.life() + random.nextInt(kind.lifeJitter() + 1);
        float floor = floor(kind);
        this.hot = AccentPlan.lift(options.hot(), kind.hot() > 0.0F ? AccentPlan.LIGHT_SPRITE_FLOOR : floor);
        this.body = AccentPlan.lift(options.body(), floor);
        this.cool = AccentPlan.lift(options.cool(), floor);
        this.baseSize = 0.1F * kind.size() * options.scale() * (0.8F + random.nextFloat() * 0.4F);
        this.quadSize = baseSize;
        boolean tumbles = kind.frameMode() == MatterKind.Frames.PICK;
        this.spin = tumbles ? (random.nextFloat() - 0.5F) * 0.5F : 0.0F;
        this.roll = tumbles ? random.nextFloat() * Mth.TWO_PI : 0.0F;
        this.oRoll = roll;
        this.phase = random.nextFloat() * Mth.TWO_PI;
        frame(kind.frameMode() == MatterKind.Frames.PICK ? random.nextInt(kind.frames()) : 0);
        colour(0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) {
            return;
        }
        if (TintedSpriteParticle.onTheLens(x, y, z)) {
            remove();
            return;
        }
        float t = age / (float) lifetime;
        oRoll = roll;
        if (spin != 0.0F && !onGround && !settled) {
            roll += spin;
        }
        if (kind.shape() == MatterKind.Shape.SWAY && !onGround) {
            xd += Mth.sin(age * SWAY_RATE + phase) * SWAY;
            zd += Mth.cos(age * SWAY_RATE + phase) * SWAY;
        }
        switch (kind.frameMode()) {
            case AGE -> frame(kind.frameAt(t));
            case SETTLE -> frame(settled ? (age - settledAt < SPLAT_SPREAD_AT ? 2 : 3) : (yd < -TEARDROP_SPEED ? 1 : 0));
            case PICK -> { }
        }
        quadSize = baseSize * kind.grow(t);
        alpha = settled ? Math.max(0.0F, 1.0F - (age - settledAt) / (float) SPLAT_TICKS) : kind.alpha(t);
        colour(t);
    }

    /**
     * Collision done here rather than by vanilla, which stops a particle for good the first time it
     * is blocked. A kind with a bounce keeps that much of its speed off whatever it hit; a drop
     * lands and lies where it fell.
     */
    @Override
    public void move(double dx, double dy, double dz) {
        if (settled) {
            return;
        }
        Vec3 wanted = new Vec3(dx, dy, dz);
        Vec3 got = wanted;
        double lengthSqr = wanted.lengthSqr();
        if (hasPhysics && lengthSqr > 0.0D && lengthSqr < MAX_COLLIDE_SQR) {
            got = Entity.collideBoundingBox(null, wanted, getBoundingBox(), level, List.of());
        }
        if (got.lengthSqr() > 0.0D) {
            setBoundingBox(getBoundingBox().move(got));
            setLocationFromBoundingbox();
        }
        boolean blockedX = Math.abs(dx - got.x) > 1.0E-7D;
        boolean blockedY = Math.abs(dy - got.y) > 1.0E-7D;
        boolean blockedZ = Math.abs(dz - got.z) > 1.0E-7D;
        onGround = blockedY && dy < 0.0D;
        if (blockedX) {
            xd = -xd * kind.bounce();
        }
        if (blockedZ) {
            zd = -zd * kind.bounce();
        }
        if (!blockedY) {
            return;
        }
        if (onGround && kind.frameMode() == MatterKind.Frames.SETTLE) {
            settle();
        } else if (onGround && kind.bounce() > 0.0F && bounces < MAX_BOUNCES && -dy > MIN_BOUNCE) {
            yd = -dy * kind.bounce();
            bounces++;
        } else {
            yd = 0.0D;
        }
    }

    private void settle() {
        settled = true;
        settledAt = age;
        xd = 0.0D;
        yd = 0.0D;
        zd = 0.0D;
        gravity = 0.0F;
        lifetime = Math.min(lifetime, age + SPLAT_TICKS);
        roll = random.nextFloat() * Mth.TWO_PI;
        oRoll = roll;
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        if (kind.shape() != MatterKind.Shape.STRETCH || !streak(buffer, camera, partialTick)) {
            super.render(buffer, camera, partialTick);
        }
    }

    /**
     * A quad laid along the particle's flight and turned about it to face the camera, longer the
     * faster it goes. The sprite's +x runs along the flight, so a gust's curl leads with its head.
     */
    private boolean streak(VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 cam = camera.getPosition();
        float px = (float) (Mth.lerp(partialTick, xo, x) - cam.x);
        float py = (float) (Mth.lerp(partialTick, yo, y) - cam.y);
        float pz = (float) (Mth.lerp(partialTick, zo, z) - cam.z);
        Vector3f along = new Vector3f((float) xd, (float) yd, (float) zd);
        float speed = along.length();
        Vector3f toCamera = new Vector3f(-px, -py, -pz);
        if (speed < 1.0E-3F || toCamera.lengthSquared() < 1.0E-6F) {
            return false;
        }
        along.div(speed);
        Vector3f up = toCamera.normalize().cross(along, new Vector3f());
        if (up.lengthSquared() < 1.0E-6F) {
            return false;
        }
        up.normalize();
        float size = getQuadSize(partialTick);
        float length = size * Math.min(STRETCH_MAX, 1.0F + STRETCH_PER_SPEED * speed);
        float u0 = getU0();
        float u1 = getU1();
        float v0 = getV0();
        float v1 = getV1();
        int light = getLightColor(partialTick);
        corner(buffer, px, py, pz, along, length, up, -size, u1, v1, light);
        corner(buffer, px, py, pz, along, length, up, size, u1, v0, light);
        corner(buffer, px, py, pz, along, -length, up, size, u0, v0, light);
        corner(buffer, px, py, pz, along, -length, up, -size, u0, v1, light);
        return true;
    }

    private void corner(VertexConsumer buffer, float px, float py, float pz, Vector3f along, float a, Vector3f up,
            float b, float u, float v, int light) {
        buffer.addVertex(px + along.x * a + up.x * b, py + along.y * a + up.y * b, pz + along.z * a + up.z * b)
                .setUv(u, v)
                .setColor(rCol, gCol, bCol, alpha)
                .setLight(light);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return kind.fullBright(age / (float) lifetime) ? 0xF000F0 : super.getLightColor(partialTick);
    }

    /** Shows frame {@code frame} of this kind: the sprite list is every kind's frames end to end. */
    private void frame(int frame) {
        // SpriteSet.get(age, lifetime) indexes age * (size - 1) / lifetime, so a lifetime of size - 1
        // hands back exactly the sprite at that index.
        setSprite(sprites.get(kind.offset() + Mth.clamp(frame, 0, kind.frames() - 1), MatterKind.SPRITES - 1));
    }

    private void colour(float t) {
        float r = kind.ramp(t);
        int rgb = r < 1.0F ? mix(hot, body, r) : mix(body, cool, r - 1.0F);
        setColor(((rgb >> 16) & 0xFF) / 255.0F, ((rgb >> 8) & 0xFF) / 255.0F, (rgb & 0xFF) / 255.0F);
    }

    private static int mix(int from, int to, float t) {
        float f = Mth.clamp(t, 0.0F, 1.0F);
        int r = Math.round(Mth.lerp(f, (from >> 16) & 0xFF, (to >> 16) & 0xFF));
        int g = Math.round(Mth.lerp(f, (from >> 8) & 0xFF, (to >> 8) & 0xFF));
        int b = Math.round(Mth.lerp(f, from & 0xFF, to & 0xFF));
        return (r << 16) | (g << 8) | b;
    }

    /**
     * The darkest a kind may be drawn, as the mod's other sprites are held: light is never dim,
     * smoke is never a black card, and a crumb is never a hole in the picture.
     */
    private static float floor(MatterKind kind) {
        if (kind.shape() == MatterKind.Shape.GROW) {
            return AccentPlan.WISP_FLOOR;
        }
        return kind.hot() >= 1.0F ? AccentPlan.LIGHT_SPRITE_FLOOR : AccentPlan.SHARD_FLOOR;
    }

    /** The one provider, over the one sprite list every kind shares. */
    public static final class Provider implements ParticleProvider<ForgeMatterOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(ForgeMatterOptions options, ClientLevel level, double x, double y, double z,
                double xd, double yd, double zd) {
            if (TintedSpriteParticle.onTheLens(x, y, z)) {
                return null;
            }
            return new ForgeMatterParticle(level, x, y, z, xd, yd, zd, options, sprites);
        }
    }
}
