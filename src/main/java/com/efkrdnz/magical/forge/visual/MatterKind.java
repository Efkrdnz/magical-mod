package com.efkrdnz.magical.forge.visual;

import java.util.Locale;
import java.util.Optional;

/**
 * Every kind of matter a forged strike throws, and how each one moves and is drawn.
 *
 * <p>The declaration order is the order of {@code particles/forge_matter.json}, which is one sprite
 * list for all of them: a kind's frames start at {@link #offset()} and run for {@link #frames()}.
 * Reordering this enum without regenerating that file draws every kind in another kind's sprites,
 * and nothing but {@code ForgeSpritesTest} would notice.
 *
 * <p>Gravity is vanilla's particle field, spent as {@code 0.04 * gravity} of a block a tick; a block
 * crumb is 1. Friction is the fraction of its speed a particle keeps each tick. {@code hot} is how
 * much of its life it spends burning - drawn full bright and ramping from its hot colour to its body
 * colour - before the world lights it and it cools toward its last colour; one is a thing that is
 * light all its life, zero a thing that never was.
 *
 * <p>Pure: no Minecraft, so the table is pinned on exact values.
 */
public enum MatterKind {
    /** A struck spark: fast, stretched along its flight, bounces once or twice. */
    SPARK("forge/spark", 4, Frames.AGE, Shape.STRETCH, 0.96f, 0.9f, 0.45f, 1.0f, 10, 8, 0.8f),
    /** A coal: flies slower, glows, then cools and crumbles. */
    EMBER("forge/ember", 6, Frames.AGE, Shape.ROUND, 0.94f, 0.35f, 0.2f, 0.6f, 24, 14, 1.1f),
    /** A flake of ash: hangs, sways, settles slowly. */
    ASH("forge/ash", 4, Frames.PICK, Shape.SWAY, 0.92f, 0.08f, 0.0f, 0.0f, 30, 20, 0.9f),
    /** A snowflake: drifts and sways down. */
    FLAKE("forge/flake", 4, Frames.PICK, Shape.SWAY, 0.9f, 0.1f, 0.0f, 0.0f, 30, 16, 1.1f),
    /** A drop: falls, and splats flat where it lands. */
    DROP("forge/drop", 4, Frames.SETTLE, Shape.ROUND, 0.98f, 1.0f, 0.0f, 0.0f, 22, 10, 1.0f),
    /** A crumb of stone or earth: falls and bounces. */
    GRIT("forge/grit", 4, Frames.PICK, Shape.ROUND, 0.97f, 1.2f, 0.3f, 0.0f, 20, 10, 1.0f),
    /** A curl of moving air, stretched along its flight, rising a little. */
    GUST("forge/gust", 4, Frames.AGE, Shape.STRETCH, 0.88f, -0.02f, 0.0f, 0.0f, 10, 5, 1.6f),
    /** A hollow: a ring closing on a point, full bright. */
    HOLLOW("forge/hollow", 4, Frames.AGE, Shape.ROUND, 0.85f, 0.0f, 0.0f, 1.0f, 12, 4, 1.4f),
    /** The flash of a blade biting: large, brief, full bright. */
    NICK("forge/nick", 4, Frames.AGE, Shape.ROUND, 1.0f, 0.0f, 0.0f, 1.0f, 5, 0, 3.0f),
    /** A chip of ice or crystal: tumbles, falls, bounces. The mod's shard sprites. */
    CHIP("shard", 4, Frames.PICK, Shape.ROUND, 0.96f, 1.0f, 0.4f, 0.0f, 20, 10, 1.1f),
    /** A twinkle of light, rising. The mod's mote sprites. */
    GLINT("mote", 4, Frames.AGE, Shape.ROUND, 0.9f, -0.05f, 0.0f, 1.0f, 14, 8, 1.0f),
    /** A puff of smoke that rises, grows and thins. The mod's wisp sprites. */
    SMOKE("wisp", 8, Frames.AGE, Shape.GROW, 0.93f, -0.1f, 0.0f, 0.0f, 26, 14, 2.0f);

    /** How a kind picks its sprite: one for its life, through its frames as it ages, or by landing. */
    public enum Frames { PICK, AGE, SETTLE }

    /** How a kind's quad is laid out and sized over its life. */
    public enum Shape { ROUND, STRETCH, SWAY, GROW }

    /** Where a solid kind starts to fade. */
    private static final float FADE_FROM = 0.7f;
    /** Smoke is never more than half there: drawn solid it is a grey card. */
    private static final float SMOKE_OPACITY = 0.5f;

    /** How many frames every kind has between them: the length of the sprite list. */
    public static final int SPRITES;

    static {
        int at = 0;
        for (MatterKind kind : values()) {
            kind.offset = at;
            at += kind.frames;
        }
        SPRITES = at;
    }

    private final String sheet;
    private final int frames;
    private final Frames frameMode;
    private final Shape shape;
    private final float friction;
    private final float gravity;
    private final float bounce;
    private final float hot;
    private final int life;
    private final int lifeJitter;
    private final float size;
    private int offset;

    MatterKind(String sheet, int frames, Frames frameMode, Shape shape, float friction, float gravity, float bounce,
            float hot, int life, int lifeJitter, float size) {
        this.sheet = sheet;
        this.frames = frames;
        this.frameMode = frameMode;
        this.shape = shape;
        this.friction = friction;
        this.gravity = gravity;
        this.bounce = bounce;
        this.hot = hot;
        this.life = life;
        this.lifeJitter = lifeJitter;
        this.size = size;
    }

    /** The texture id of frame {@code frame}, as {@code forge_matter.json} lists it. */
    public String sprite(int frame) {
        return "magical:" + sheet + "_" + frame;
    }

    /** The path of frame {@code frame} under {@code textures/particle/}. */
    public String texture(int frame) {
        return sheet + "_" + frame;
    }

    public int offset() {
        return offset;
    }

    public int frames() {
        return frames;
    }

    public Frames frameMode() {
        return frameMode;
    }

    public Shape shape() {
        return shape;
    }

    public float friction() {
        return friction;
    }

    public float gravity() {
        return gravity;
    }

    /** The fraction of its speed a particle keeps off a floor or wall; zero stops dead. */
    public float bounce() {
        return bounce;
    }

    public float hot() {
        return hot;
    }

    public int life() {
        return life;
    }

    public int lifeJitter() {
        return lifeJitter;
    }

    /** Its size against a vanilla particle's tenth of a block. */
    public float size() {
        return size;
    }

    /** Whether the world has no say in its light at {@code t} of its life: light is not lit by anything. */
    public boolean fullBright(float t) {
        return t < hot;
    }

    /** The frame an AGE kind shows at {@code t} of its life, zero-based within its own frames. */
    public int frameAt(float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        return Math.min(frames - 1, (int) (clamped * frames));
    }

    /**
     * Where along its colour stops a particle is at {@code t} of its life: 0 its hot colour, 1 its
     * body colour, 2 its last. A burning kind reaches its body colour as it stops burning; one that
     * never burned starts there.
     */
    public float ramp(float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        if (hot <= 0.0f) {
            return 1.0f + clamped;
        }
        if (clamped < hot) {
            return clamped / hot;
        }
        return hot >= 1.0f ? 1.0f : 1.0f + (clamped - hot) / (1.0f - hot);
    }

    /** How opaque it is at {@code t} of its life. */
    public float alpha(float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        if (shape == Shape.GROW) {
            return SMOKE_OPACITY * (1.0f - clamped);
        }
        return clamped < FADE_FROM ? 1.0f : 1.0f - (clamped - FADE_FROM) / (1.0f - FADE_FROM);
    }

    /** Its size at {@code t} of its life, against its size at birth. */
    public float grow(float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        return switch (shape) {
            case GROW -> 1.0f + 1.2f * clamped;
            case STRETCH -> 1.0f - 0.5f * clamped;
            default -> this == NICK ? 0.7f + 0.6f * clamped : 1.0f;
        };
    }

    /** The name {@code /particle} takes it by. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<MatterKind> byName(String name) {
        for (MatterKind kind : values()) {
            if (kind.serializedName().equals(name)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
