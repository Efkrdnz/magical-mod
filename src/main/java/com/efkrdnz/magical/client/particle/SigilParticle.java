package com.efkrdnz.magical.client.particle;

import com.efkrdnz.magical.magic.visual.sigil.Sigil;
import com.efkrdnz.magical.magic.visual.sigil.SigilMotion;
import com.efkrdnz.magical.particle.SigilParticleOptions;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/**
 * A sigil: one symbol in a two-tone ink, drawn as two quads on one billboard - the halo in the
 * glow colour, then the strokes in the core colour. The sprite set is every core in
 * {@link Sigil} order and then every glow, so core {@code i} is sprite {@code i} and its glow is
 * sprite {@code COUNT + i}; {@code SpriteSet.get(i, size - 1)} hands back exactly sprite {@code i}.
 *
 * <p>Full bright, on the translucent sheet, velocity kept exactly as sent, and off the lens the
 * way the tinted sprites are.
 */
public final class SigilParticle extends TextureSheetParticle {
    private final TextureAtlasSprite glowSprite;
    private final float glowRed;
    private final float glowGreen;
    private final float glowBlue;
    private final SigilMotion motion;
    private final float baseSize;
    private final float swayPhase;

    private SigilParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
            SigilParticleOptions options, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.motion = options.motion();
        this.friction = motion.friction();
        this.gravity = motion.gravity();
        this.hasPhysics = motion.physics();
        this.lifetime = motion.life() + random.nextInt(motion.lifeJitter() + 1);
        int last = Sigil.COUNT * 2 - 1;
        setSprite(sprites.get(options.sigil().ordinal(), last));
        this.glowSprite = sprites.get(Sigil.COUNT + options.sigil().ordinal(), last);
        int core = options.core();
        setColor((core >> 16 & 0xFF) / 255.0F, (core >> 8 & 0xFF) / 255.0F, (core & 0xFF) / 255.0F);
        int glow = options.glow();
        this.glowRed = (glow >> 16 & 0xFF) / 255.0F;
        this.glowGreen = (glow >> 8 & 0xFF) / 255.0F;
        this.glowBlue = (glow & 0xFF) / 255.0F;
        this.baseSize = 0.1F * options.scale();
        this.quadSize = baseSize;
        this.swayPhase = random.nextFloat() * Mth.TWO_PI;
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
        if (motion.sway() > 0.0F && !onGround) {
            xd += Mth.cos(age * 0.35F + swayPhase) * motion.sway();
            zd += Mth.sin(age * 0.35F + swayPhase) * motion.sway();
        }
        float t = age / (float) lifetime;
        alpha = motion.alpha(t);
        quadSize = baseSize * motion.size(t);
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTicks) {
        // the halo first, then the strokes over it; the two masks never share a texel
        TextureAtlasSprite core = sprite;
        float red = rCol;
        float green = gCol;
        float blue = bCol;
        sprite = glowSprite;
        rCol = glowRed;
        gCol = glowGreen;
        bCol = glowBlue;
        super.render(buffer, camera, partialTicks);
        sprite = core;
        rCol = red;
        gCol = green;
        bCol = blue;
        super.render(buffer, camera, partialTicks);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    public static final class Provider implements ParticleProvider<SigilParticleOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SigilParticleOptions options, ClientLevel level, double x, double y, double z,
                double xd, double yd, double zd) {
            // not on the lens: see AccentPlan.SPRITE_NEAR_CAMERA
            if (TintedSpriteParticle.onTheLens(x, y, z)) {
                return null;
            }
            return new SigilParticle(level, x, y, z, xd, yd, zd, options, sprites);
        }
    }
}
