package com.efkrdnz.magical.boss.unwaking;

import static org.junit.jupiter.api.Assertions.*;
import com.efkrdnz.magical.network.UnwakingSnapshotPayload;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class UnwakingSnapshotTest {
    @Test void domainSnapshotPreservesLockedGeometryRecipientsAndRecovery() {
        var recipient=UUID.randomUUID();
        var hazard=new UnwakingHazard(8,UnwakingHazard.Kind.VORTEX_BEAM,200,new Vec3(300,236,50),new Vec3(0,-1,0),160,2,List.of(new Vec3(300,76,50),new Vec3(300,140,50)),recipient);
        var hazards=new java.util.ArrayList<UnwakingHazard>(); hazards.add(hazard);
        for(var kind:List.of(UnwakingHazard.Kind.FIRMAMENT_GUILLOTINE,UnwakingHazard.Kind.SIXFOLD_BURIAL,UnwakingHazard.Kind.NULL_HORIZON))
            hazards.add(new UnwakingHazard(hazards.size()+8,kind,500, new Vec3(420,160,-96),new Vec3(0,0,1),192,0,List.of(new Vec3(420,160,96)),recipient));
        var assault=new UnwakingAssaultState(2,200,new Vec3(300,140,50),new Vec3(1,0,0),new Vec3(310,142,52),320);
        var packet=new UnwakingSnapshotPayload(UUID.randomUUID(),44,220,UnwakingPhase.TRIAL_SKY,100,ResourceLocation.fromNamespaceAndPath("magical","chronos_end"),9,-1,Vec3.ZERO,new Vec3(0,0,1),2,false,hazards,3,true,70,51,"white_becomes_law",2,true,true,assault);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        try {
            UnwakingSnapshotPayload.STREAM_CODEC.encode(buffer,packet);
            assertEquals(packet,UnwakingSnapshotPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0,buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
