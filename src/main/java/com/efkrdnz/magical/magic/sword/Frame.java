package com.efkrdnz.magical.magic.sword;

/**
 * The one movable thing the whole Array is rigid to: an origin, a facing and a scale.
 *
 * <p>Six numbers, and every verb in the class is a move of them. That is the argument for the
 * scale being a <em>frame</em> field rather than a per-blade one: One Blade needs no special case
 * in the renderer at all, because the convergence is the renderer reading a scale that is driving
 * to zero, and a shed is the same scale having gone the other way.
 *
 * <p>{@code yaw} and {@code pitch} are Minecraft's own, in degrees, and Minecraft's pitch is
 * positive <em>downward</em> - the opposite sign to {@link Station#pitch()}, which is an
 * elevation. {@code ArrayPose} is the only place the two meet and it is the only place they may.
 *
 * <p>Never saved. The wielder keeps the shape; the frame is rebuilt from the bind every read.
 */
public record Frame(double x, double y, double z, float yaw, float pitch, float scale) {

    public Frame withScale(float scale) {
        return new Frame(x, y, z, yaw, pitch, scale);
    }

    public Frame withOrigin(double x, double y, double z) {
        return new Frame(x, y, z, yaw, pitch, scale);
    }

    public Frame withFacing(float yaw, float pitch) {
        return new Frame(x, y, z, yaw, pitch, scale);
    }
}
