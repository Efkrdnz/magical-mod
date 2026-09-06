package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Play-to-client: a transient visual event (windup circle, release, impact, decal, barrier hit,
 * zone tick) resolved client-side from the skill's VisualProfile. No entity, no tracking.
 *
 * @param skillIndex index into MagicContent.orderedSkillIds()
 * @param cue one of the CUE_* constants
 * @param dir direction or surface normal (unit vector)
 * @param victimId entity id for cues attached to a victim, else -1
 * @param sneak sneak variation flag (mirrored spin / reversed polarity look)
 */
public record VisualCuePayload(int skillIndex, int cue, Vec3 pos, Vec3 dir, int seed, int victimId, float scale, boolean sneak) implements CustomPacketPayload {
    public static final int CUE_CAST_WINDUP = 0;
    public static final int CUE_RELEASE = 1;
    public static final int CUE_IMPACT = 2;
    public static final int CUE_DECAL = 3;
    public static final int CUE_BARRIER_HIT = 4;
    public static final int CUE_ZONE_TICK = 5;
    public static final int CUE_MUZZLE = 6;
    public static final int CUE_PARTICLE_BURST = 7;

    public static final Type<VisualCuePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "visual_cue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VisualCuePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.skillIndex);
                buf.writeByte(payload.cue);
                buf.writeDouble(payload.pos.x);
                buf.writeDouble(payload.pos.y);
                buf.writeDouble(payload.pos.z);
                buf.writeFloat((float) payload.dir.x);
                buf.writeFloat((float) payload.dir.y);
                buf.writeFloat((float) payload.dir.z);
                buf.writeByte(payload.seed);
                buf.writeVarInt(payload.victimId);
                buf.writeFloat(payload.scale);
                buf.writeBoolean(payload.sneak);
            },
            buf -> new VisualCuePayload(
                    buf.readVarInt(),
                    buf.readByte(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()),
                    buf.readByte() & 63,
                    buf.readVarInt(),
                    buf.readFloat(),
                    buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
