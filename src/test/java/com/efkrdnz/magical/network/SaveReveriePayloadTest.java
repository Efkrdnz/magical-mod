package com.efkrdnz.magical.network;

import com.efkrdnz.magical.magic.mind.Lexicon;
import com.efkrdnz.magical.magic.mind.Offset;
import com.efkrdnz.magical.magic.mind.Reaction;
import com.efkrdnz.magical.magic.mind.Reverie;
import com.efkrdnz.magical.magic.mind.ReverieNbt;
import com.efkrdnz.magical.magic.mind.Script;
import com.efkrdnz.magical.magic.mind.Sense;
import com.efkrdnz.magical.magic.mind.Stance;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SaveReveriePayloadTest {
    @Test
    void aDraftRoundTrips() {
        CompoundTag data = new CompoundTag();
        data.putString("name", "Pit");
        SaveReveriePayload packet = new SaveReveriePayload(2, data);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SaveReveriePayload.STREAM_CODEC.encode(buffer, packet);
            assertEquals(packet, SaveReveriePayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void aDraftPastTheCapIsRefusedOnTheWayIn() {
        CompoundTag data = new CompoundTag();
        net.minecraft.nbt.ListTag names = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < 1200; i++) {
            names.add(net.minecraft.nbt.StringTag.valueOf("x".repeat(2000)));
        }
        data.put("names", names);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SaveReveriePayload.STREAM_CODEC.encode(buffer, new SaveReveriePayload(0, data));
            assertThrows(RuntimeException.class, () -> SaveReveriePayload.STREAM_CODEC.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void aMaximalLegitimateDraftDecodesWithinTheQuota() {
        Lexicon lexicon = new Lexicon();
        lexicon.gaze("block:minecraft:waxed_weathered_cut_copper_stairs");
        lexicon.gaze("creature:minecraft:zombified_piglin");
        for (int i = 0; lexicon.budget() < Lexicon.MAX_BUDGET; i++) {
            lexicon.gaze("block:minecraft:filler_" + i);
        }
        Reverie reverie = new Reverie();
        reverie.setName("n".repeat(Reverie.MAX_NAME_LENGTH));
        for (int i = 0; i < 100; i++) {
            reverie.addBlock(new Offset(i % 10 - 5, i / 10 - 5, 0), "minecraft:waxed_weathered_cut_copper_stairs", lexicon);
        }
        for (int i = 0; i < 28; i++) {
            reverie.addFigment(new Offset(i - 14, 0, 3), "minecraft:zombified_piglin", lexicon);
            reverie.setScript(i, new Script(Stance.FOLLOW, Reaction.CHASE));
            reverie.setFigmentSenses(i, java.util.EnumSet.allOf(Sense.class));
        }
        assertEquals(Lexicon.MAX_BUDGET, reverie.size());
        CompoundTag data = ReverieNbt.save(reverie);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            SaveReveriePayload.STREAM_CODEC.encode(buffer, new SaveReveriePayload(2, data));
            SaveReveriePayload decoded = SaveReveriePayload.STREAM_CODEC.decode(buffer);
            assertEquals(data, decoded.data());
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }
}
