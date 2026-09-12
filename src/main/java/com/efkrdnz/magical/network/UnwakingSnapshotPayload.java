package com.efkrdnz.magical.network;

import com.efkrdnz.magical.MagicalMod;
import com.efkrdnz.magical.boss.unwaking.UnwakingPhase;
import com.efkrdnz.magical.boss.unwaking.UnwakingHazard;
import java.util.List;
import com.efkrdnz.magical.boss.unwaking.UnwakingAssaultState;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

public record UnwakingSnapshotPayload(UUID run, long revision, long tick, UnwakingPhase phase, long phaseStart,
        ResourceLocation dimension, int bossId, int attackTick, Vec3 origin, Vec3 direction, int feedback, boolean ended,
        List<UnwakingHazard> hazards, int fractures, boolean quiet, int recoveryTicks, long combination, String combinationName, int guardSegments, boolean hitboxes, boolean reducedEffects, UnwakingAssaultState assault) implements CustomPacketPayload {
    public UnwakingSnapshotPayload(UUID run, long revision, long tick, UnwakingPhase phase, long phaseStart,
            ResourceLocation dimension, int bossId, int attackTick, Vec3 origin, Vec3 direction, int feedback, boolean ended,
            List<UnwakingHazard> hazards, int fractures, boolean quiet, int recoveryTicks, long combination, String combinationName,
            int guardSegments, boolean hitboxes, boolean reducedEffects) {
        this(run,revision,tick,phase,phaseStart,dimension,bossId,attackTick,origin,direction,feedback,ended,hazards,fractures,quiet,recoveryTicks,
                combination,combinationName,guardSegments,hitboxes,reducedEffects,UnwakingAssaultState.NONE);
    }
    public UnwakingSnapshotPayload(UUID run, long revision, long tick, UnwakingPhase phase, long phaseStart,
            ResourceLocation dimension, int bossId, int attackTick, Vec3 origin, Vec3 direction, int feedback, boolean ended,
            List<UnwakingHazard> hazards, int fractures, boolean quiet, int recoveryTicks) {
        this(run,revision,tick,phase,phaseStart,dimension,bossId,attackTick,origin,direction,feedback,ended,hazards,fractures,quiet,recoveryTicks,0,"",3,false,false);
    }
    public static final Type<UnwakingSnapshotPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MagicalMod.MODID, "unwaking_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, UnwakingSnapshotPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeUUID(p.run); buf.writeLong(p.revision); buf.writeLong(p.tick); buf.writeEnum(p.phase); buf.writeLong(p.phaseStart);
                buf.writeResourceLocation(p.dimension); buf.writeVarInt(p.bossId); buf.writeInt(p.attackTick);
                writeVec(buf, p.origin); writeVec(buf, p.direction); buf.writeByte(p.feedback); buf.writeBoolean(p.ended);
                buf.writeVarInt(p.hazards.size()); p.hazards.forEach(h -> h.write(buf)); buf.writeVarInt(p.fractures); buf.writeBoolean(p.quiet); buf.writeVarInt(p.recoveryTicks);
                buf.writeLong(p.combination); buf.writeUtf(p.combinationName,64); buf.writeByte(p.guardSegments); buf.writeBoolean(p.hitboxes); buf.writeBoolean(p.reducedEffects); p.assault.write(buf);
            }, buf -> new UnwakingSnapshotPayload(buf.readUUID(), buf.readLong(), buf.readLong(), buf.readEnum(UnwakingPhase.class),
                    buf.readLong(), buf.readResourceLocation(), buf.readVarInt(), buf.readInt(), readVec(buf), readVec(buf), buf.readByte(), buf.readBoolean(), readHazards(buf), buf.readVarInt(), buf.readBoolean(), buf.readVarInt(),buf.readLong(),buf.readUtf(64),buf.readUnsignedByte(),buf.readBoolean(),buf.readBoolean(),UnwakingAssaultState.read(buf)));

    private static List<UnwakingHazard> readHazards(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt(); if (count < 0 || count > 16) throw new IllegalArgumentException("Too many encounter hazards");
        java.util.ArrayList<UnwakingHazard> hazards = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) hazards.add(UnwakingHazard.read(buf)); return List.copyOf(hazards);
    }

    private static void writeVec(RegistryFriendlyByteBuf buf, Vec3 v) { buf.writeDouble(v.x); buf.writeDouble(v.y); buf.writeDouble(v.z); }
    private static Vec3 readVec(RegistryFriendlyByteBuf buf) { return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()); }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
