package com.efkrdnz.magical.magic.visual;

/** Which palette slot a circle layer or silhouette is tinted with. */
public enum ColorRole {
    BASE,
    BRIGHT,
    HOT,
    DIM,
    /**
     * Not a colour: a render mode, and the only member of this enum that is one.
     *
     * <p>{@code GlyphCirclePainter} switches on the role rather than on the tint - a layer
     * wearing INK goes to {@code MagicalFxRenderTypes.glyphVoid()}, which is straight alpha
     * ({@code SRC_ALPHA, ONE_MINUS_SRC_ALPHA}) rather than the additive {@code ONE, ONE} every
     * other role gets, and the shader's matching branch paints {@code tint * 0.08}. So an INK
     * member does not add light to the world, it <em>replaces</em> it: at full coverage it is an
     * opaque near-black stroke over terrain, over bodies and over the sky.
     *
     * <p>That is the point for a school whose identity is darkness - Blood, Dark, Void, Eldritch,
     * where {@code CoreKind.VOID_PIT} is drawn this way so it can darken the ground. Anywhere
     * else it is a black scribble on a daylight frame, and it is a scribble that compiles, passes
     * and renders without a warning. The Sword school shipped one on Loose's cast circle;
     * {@code SwordCircleContrastTest} is what holds that school to it now.
     */
    INK,
    ACCENT
}
