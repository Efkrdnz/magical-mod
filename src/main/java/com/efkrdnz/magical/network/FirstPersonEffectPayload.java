package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * First-person feedback: a screen overlay (fp_overlay kind + colour), camera shake, hitstop
 * freeze and an FOV kick. overlayKind selects the shader shape, intensity 0..63 its strength and
 * hitYaw (0..31, 32 = omnidirectional) leans a vignette toward where a hit came from.
 */
public record FirstPersonEffectPayload(
        int color,
        int overlayTicks,
        float maxAlpha,
        int shakeTicks,
        float shakeStrength,
        int freezeTicks,
        float fovKick,
        int overlayKind,
        int intensity,
        int hitYaw) implements CustomPacketPayload {
    public static final Type<FirstPersonEffectPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "first_person_effect"));
    public static final int OMNI = 32;

    public static final net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, FirstPersonEffectPayload> STREAM_CODEC =
            net.minecraft.network.codec.StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeInt(payload.color);
                        buf.writeInt(payload.overlayTicks);
                        buf.writeFloat(payload.maxAlpha);
                        buf.writeInt(payload.shakeTicks);
                        buf.writeFloat(payload.shakeStrength);
                        buf.writeInt(payload.freezeTicks);
                        buf.writeFloat(payload.fovKick);
                        buf.writeByte(payload.overlayKind);
                        buf.writeByte(payload.intensity);
                        buf.writeByte(payload.hitYaw);
                    },
                    buf -> new FirstPersonEffectPayload(
                            buf.readInt(),
                            buf.readInt(),
                            buf.readFloat(),
                            buf.readInt(),
                            buf.readFloat(),
                            buf.readInt(),
                            buf.readFloat(),
                            buf.readByte(),
                            buf.readByte(),
                            buf.readByte()));

    /** Legacy 7-argument form: plain vignette, full strength, omnidirectional. */
    public FirstPersonEffectPayload(int color, int overlayTicks, float maxAlpha, int shakeTicks, float shakeStrength, int freezeTicks, float fovKick) {
        this(color, overlayTicks, maxAlpha, shakeTicks, shakeStrength, freezeTicks, fovKick, 0, 63, OMNI);
    }

    public static FirstPersonEffectPayload vignette(int color, int ticks, float alpha) {
        return new FirstPersonEffectPayload(color, ticks, alpha, 0, 0.0F, 0, 0.0F);
    }

    public static FirstPersonEffectPayload shake(int ticks, float strength) {
        return new FirstPersonEffectPayload(0x000000, 0, 0.0F, ticks, strength, 0, 0.0F);
    }

    public static FirstPersonEffectPayload impact(int color, int overlayTicks, float alpha, int shakeTicks, float shakeStrength, int freezeTicks, float fovKick) {
        return new FirstPersonEffectPayload(color, overlayTicks, alpha, shakeTicks, shakeStrength, freezeTicks, fovKick);
    }

    public FirstPersonEffectPayload withOverlay(int kind, int strength, int yawBucket) {
        return new FirstPersonEffectPayload(color, overlayTicks, maxAlpha, shakeTicks, shakeStrength, freezeTicks, fovKick, kind, strength, yawBucket);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
